package de.secretsoft.vatsim_stats.ingestion.session;

import de.secretsoft.vatsim_stats.detection.AirportEvent;
import de.secretsoft.vatsim_stats.detection.AirportEventType;
import de.secretsoft.vatsim_stats.detection.NearestAirportLookup;
import de.secretsoft.vatsim_stats.detection.PhaseDetectionConfig;
import de.secretsoft.vatsim_stats.detection.PilotPhaseStateMachine;
import de.secretsoft.vatsim_stats.detection.TrackSample;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotAirportEvent;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotAirportEventRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotSession;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotSessionRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPoint;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPointRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.SessionStatus;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
public class PilotSessionOrchestrator {

    private final PilotSessionRepository pilotSessionRepository;
    private final PilotAirportEventRepository pilotAirportEventRepository;
    private final NearestAirportLookup airportLookup;
    private final PilotTrackPointRepository pilotTrackPointRepository;

    private final ConcurrentMap<SessionKey, PilotPhaseStateMachine> stateMachines = new ConcurrentHashMap<>();
    private final ConcurrentMap<SessionKey, PilotSession> currentSessions = new ConcurrentHashMap<>();

    @Transactional
    public void processTrackPoints( List<PilotTrackPoint> trackPoints ) {
        for( PilotTrackPoint point : trackPoints ) {
            handleTrackPoint( point );
        }
    }

    @PostConstruct
    void reconstructActiveSessions() {
        for( PilotSession session : pilotSessionRepository.findByStatus( SessionStatus.ACTIVE ) ) {
            SessionKey key = new SessionKey( session.getCid(), session.getCallsign(), session.getLogonTime() );
            List<PilotTrackPoint> recentPointsNewestFirst = pilotTrackPointRepository
                .findTop10ByCidAndCallsignAndLogonTimeOrderByRecordedAtDesc(
                    session.getCid(), session.getCallsign(), session.getLogonTime() );

            PilotPhaseStateMachine machine =
                new PilotPhaseStateMachine( PhaseDetectionConfig.defaults(), airportLookup );
            List<PilotTrackPoint> chronological = new java.util.ArrayList<>( recentPointsNewestFirst );
            Collections.reverse( chronological );
            for( PilotTrackPoint point : chronological ) {
                machine.process( new TrackSample(
                    point.getRecordedAt(), point.getLatitude(), point.getLongitude(),
                    point.getAltitudeFt(), point.getGroundspeedKt() ) );
            }

            stateMachines.put( key, machine );
            currentSessions.put( key, session );
        }
    }

    private void handleTrackPoint( PilotTrackPoint point ) {
        SessionKey key = new SessionKey( point.getCid(), point.getCallsign(), point.getLogonTime() );
        PilotPhaseStateMachine machine = stateMachines.computeIfAbsent(
            key, k -> new PilotPhaseStateMachine( PhaseDetectionConfig.defaults(), airportLookup ) );
        PilotSession session = currentSessions.computeIfAbsent( key, k -> loadOrCreateSession( k, point ) );

        session = openNewLegIfFlightPlanChanged( session, point );
        session = updatePlannedFieldsIfActive( session, point );

        TrackSample sample = new TrackSample(
            point.getRecordedAt(), point.getLatitude(), point.getLongitude(),
            point.getAltitudeFt(), point.getGroundspeedKt() );
        List<AirportEvent> events = machine.process( sample );

        for( AirportEvent event : events ) {
            if( event.type() == AirportEventType.TAKEOFF && session.getStatus() == SessionStatus.COMPLETED ) {
                session = openNewLeg( session, point, event.timestamp() );
            }

            pilotAirportEventRepository.save( PilotAirportEvent.builder()
                .pilotSession( session )
                .airportIcao( event.airportIcao() )
                .eventType( event.type() )
                .occurredAt( event.timestamp() )
                .build() );

            if( event.type() == AirportEventType.LANDING ) {
                session.setStatus( SessionStatus.COMPLETED );
                session.setEndedAt( event.timestamp() );
                session = pilotSessionRepository.save( session );
            }
        }

        currentSessions.put( key, session );
    }

    private PilotSession loadOrCreateSession( SessionKey key, PilotTrackPoint point ) {
        return pilotSessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc( key.cid(), key.callsign(), key.logonTime() )
            .orElseGet( () -> createSession( key, 0, point.getFlightPlanDeparture(), point.getFlightPlanDestination(),
                point.getAircraftShort(), point.getRecordedAt() ) );
    }

    private PilotSession createSession(
        SessionKey key, int sequenceNumber, String plannedDeparture, String plannedDestination,
        String aircraftShort, Instant startedAt ) {

        PilotSession session = PilotSession.builder()
            .cid( key.cid() )
            .callsign( key.callsign() )
            .logonTime( key.logonTime() )
            .sequenceNumber( sequenceNumber )
            .plannedDeparture( plannedDeparture )
            .plannedDestination( plannedDestination )
            .aircraftShort( aircraftShort )
            .status( SessionStatus.ACTIVE )
            .startedAt( startedAt )
            .build();
        return pilotSessionRepository.save( session );
    }

    private PilotSession openNewLeg( PilotSession completed, PilotTrackPoint point, Instant startedAt ) {
        return createSession(
            new SessionKey( completed.getCid(), completed.getCallsign(), completed.getLogonTime() ),
            completed.getSequenceNumber() + 1,
            point.getFlightPlanDeparture(), point.getFlightPlanDestination(), point.getAircraftShort(),
            startedAt );
    }

    private PilotSession openNewLegIfFlightPlanChanged( PilotSession session, PilotTrackPoint point ) {
        if( session.getStatus() != SessionStatus.COMPLETED ) {
            return session;
        }
        boolean hasAnyPlan = point.getFlightPlanDeparture() != null || point.getFlightPlanDestination() != null;
        boolean changed = !Objects.equals( point.getFlightPlanDeparture(), session.getPlannedDeparture() )
            || !Objects.equals( point.getFlightPlanDestination(), session.getPlannedDestination() );
        if( hasAnyPlan && changed ) {
            return openNewLeg( session, point, point.getRecordedAt() );
        }
        return session;
    }

    private PilotSession updatePlannedFieldsIfActive( PilotSession session, PilotTrackPoint point ) {
        if( session.getStatus() != SessionStatus.ACTIVE ) {
            return session;
        }
        boolean changed = false;
        if( !Objects.equals( session.getPlannedDeparture(), point.getFlightPlanDeparture() ) ) {
            session.setPlannedDeparture( point.getFlightPlanDeparture() );
            changed = true;
        }
        if( !Objects.equals( session.getPlannedDestination(), point.getFlightPlanDestination() ) ) {
            session.setPlannedDestination( point.getFlightPlanDestination() );
            changed = true;
        }
        if( !Objects.equals( session.getAircraftShort(), point.getAircraftShort() ) ) {
            session.setAircraftShort( point.getAircraftShort() );
            changed = true;
        }
        if( changed ) {
            session = pilotSessionRepository.save( session );
        }
        return session;
    }
}
