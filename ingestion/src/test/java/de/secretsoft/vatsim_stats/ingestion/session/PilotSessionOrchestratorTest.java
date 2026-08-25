package de.secretsoft.vatsim_stats.ingestion.session;

import de.secretsoft.vatsim_stats.detection.AirportRef;
import de.secretsoft.vatsim_stats.detection.NearestAirportLookup;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotAirportEventRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotSession;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPoint;
import de.secretsoft.vatsim_stats.ingestion.domain.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PilotSessionOrchestratorTest {

    private static final AirportRef EDDF = new AirportRef( "EDDF", 50.0264, 8.5431, 364 );
    private static final Instant LOGON = Instant.parse( "2026-08-24T09:00:00Z" );

    private final NearestAirportLookup lookup = ( lat, lon, radius ) -> Optional.of( EDDF );

    private InMemoryPilotSessionRepository sessionRepository;
    private PilotAirportEventRepository eventRepository;
    private PilotSessionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        sessionRepository = new InMemoryPilotSessionRepository();
        eventRepository = mock( PilotAirportEventRepository.class );
        when( eventRepository.save( org.mockito.ArgumentMatchers.any() ) )
            .thenAnswer( invocation -> invocation.getArgument( 0 ) );
        orchestrator = new PilotSessionOrchestrator( sessionRepository, eventRepository, lookup );
    }

    private PilotTrackPoint point( int offsetSeconds, double altitudeFt, double groundspeedKt,
                                    String departure, String destination ) {
        return PilotTrackPoint.builder()
            .recordedAt( LOGON.plusSeconds( offsetSeconds ) )
            .cid( 123456L )
            .callsign( "DLH400" )
            .logonTime( LOGON )
            .latitude( 50.0 )
            .longitude( 8.5 )
            .altitudeFt( (int) altitudeFt )
            .groundspeedKt( (int) groundspeedKt )
            .flightPlanDeparture( departure )
            .flightPlanDestination( destination )
            .aircraftShort( "A320" )
            .build();
    }

    @Test
    void createsAnActiveSessionOnFirstTrackPoint() {
        orchestrator.processTrackPoints( List.of( point( 0, 3000, 250, "EDDF", "EDDM" ) ) );

        PilotSession session = sessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc( 123456L, "DLH400", LOGON )
            .orElseThrow();
        assertThat( session.getStatus() ).isEqualTo( SessionStatus.ACTIVE );
        assertThat( session.getSequenceNumber() ).isZero();
        assertThat( session.getPlannedDeparture() ).isEqualTo( "EDDF" );
        assertThat( session.getPlannedDestination() ).isEqualTo( "EDDM" );
    }

    @Test
    void completesSessionAfterLandingDwellThresholdAndOpensNewLegOnRefile() {
        orchestrator.processTrackPoints( List.of( point( 0, 3000, 250, "EDDF", "EDDM" ) ) );
        for( int offset = 15; offset <= 120; offset += 15 ) {
            orchestrator.processTrackPoints( List.of( point( offset, 550, 15, "EDDF", "EDDM" ) ) );
        }

        PilotSession firstLeg = sessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc( 123456L, "DLH400", LOGON )
            .orElseThrow();
        assertThat( firstLeg.getStatus() ).isEqualTo( SessionStatus.COMPLETED );
        assertThat( firstLeg.getSequenceNumber() ).isZero();

        orchestrator.processTrackPoints( List.of( point( 135, 550, 5, "EDDF", "EDDL" ) ) );

        PilotSession secondLeg = sessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc( 123456L, "DLH400", LOGON )
            .orElseThrow();
        assertThat( secondLeg.getSequenceNumber() ).isEqualTo( 1 );
        assertThat( secondLeg.getStatus() ).isEqualTo( SessionStatus.ACTIVE );
        assertThat( secondLeg.getPlannedDestination() ).isEqualTo( "EDDL" );
    }

    @Test
    void opensNewLegOnTakeoffAfterCompletedSessionEvenWithoutAFlightPlanChange() {
        orchestrator.processTrackPoints( List.of( point( 0, 3000, 250, null, null ) ) );
        for( int offset = 15; offset <= 120; offset += 15 ) {
            orchestrator.processTrackPoints( List.of( point( offset, 550, 15, null, null ) ) );
        }
        PilotSession firstLeg = sessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc( 123456L, "DLH400", LOGON )
            .orElseThrow();
        assertThat( firstLeg.getStatus() ).isEqualTo( SessionStatus.COMPLETED );

        orchestrator.processTrackPoints( List.of( point( 135, 3000, 180, null, null ) ) );

        PilotSession secondLeg = sessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc( 123456L, "DLH400", LOGON )
            .orElseThrow();
        assertThat( secondLeg.getSequenceNumber() ).isEqualTo( 1 );
        assertThat( secondLeg.getStatus() ).isEqualTo( SessionStatus.ACTIVE );
    }

    @Test
    void diversionWhileAirborneUpdatesTheSameSessionInstead() {
        orchestrator.processTrackPoints( List.of( point( 0, 3000, 250, "EDDF", "EDDM" ) ) );
        orchestrator.processTrackPoints( List.of( point( 15, 3000, 250, "EDDF", "EDDL" ) ) );

        PilotSession session = sessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc( 123456L, "DLH400", LOGON )
            .orElseThrow();
        assertThat( session.getSequenceNumber() ).isZero();
        assertThat( session.getStatus() ).isEqualTo( SessionStatus.ACTIVE );
        assertThat( session.getPlannedDestination() ).isEqualTo( "EDDL" );
    }

}
