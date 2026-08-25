package de.secretsoft.vatsim_stats.ingestion.session;

import de.secretsoft.vatsim_stats.detection.AirportEventType;
import de.secretsoft.vatsim_stats.detection.AirportRef;
import de.secretsoft.vatsim_stats.detection.NearestAirportLookup;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotAirportEvent;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotAirportEventRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotSession;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPoint;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPointRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.SessionStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PilotSessionRestartReconstructionTest {

    private static final AirportRef EDDF = new AirportRef( "EDDF", 50.0264, 8.5431, 364 );
    private static final Instant LOGON = Instant.parse( "2026-08-24T09:00:00Z" );
    private static final NearestAirportLookup ALWAYS_EDDF = ( lat, lon, radius ) -> Optional.of( EDDF );

    @Test
    void resumesAGroundPendingSessionAfterRestartAndEmitsLandingOnceDwellIsReached() {
        InMemoryPilotSessionRepository sessionRepository = new InMemoryPilotSessionRepository();
        PilotSession activeSession = sessionRepository.save( PilotSession.builder()
            .cid( 123456L )
            .callsign( "DLH400" )
            .logonTime( LOGON )
            .sequenceNumber( 0 )
            .status( SessionStatus.ACTIVE )
            .startedAt( LOGON )
            .build() );

        PilotTrackPointRepository trackPointRepository = mock( PilotTrackPointRepository.class );
        when( trackPointRepository.findTop10ByCidAndCallsignAndLogonTimeOrderByRecordedAtDesc(
            123456L, "DLH400", LOGON ) ).thenReturn( List.of(
            trackPoint( 30, 550, 15 ),
            trackPoint( 15, 3000, 250 ),
            trackPoint( 0, 3000, 250 )
        ) );

        PilotAirportEventRepository eventRepository = mock( PilotAirportEventRepository.class );
        when( eventRepository.save( any() ) ).thenAnswer( invocation -> invocation.getArgument( 0 ) );

        PilotSessionOrchestrator orchestrator = new PilotSessionOrchestrator(
            sessionRepository, eventRepository, ALWAYS_EDDF, trackPointRepository );
        orchestrator.reconstructActiveSessions();

        for( int offset = 45; offset <= 120; offset += 15 ) {
            orchestrator.processTrackPoints( List.of( trackPoint( offset, 550, 15 ) ) );
        }

        ArgumentCaptor<PilotAirportEvent> eventCaptor = ArgumentCaptor.forClass( PilotAirportEvent.class );
        verify( eventRepository ).save( eventCaptor.capture() );
        assertThat( eventCaptor.getValue().getEventType() ).isEqualTo( AirportEventType.LANDING );
        assertThat( eventCaptor.getValue().getOccurredAt() ).isEqualTo( LOGON.plusSeconds( 30 ) );

        PilotSession updated = sessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc( 123456L, "DLH400", LOGON )
            .orElseThrow();
        assertThat( updated.getStatus() ).isEqualTo( SessionStatus.COMPLETED );
    }

    private static PilotTrackPoint trackPoint( int offsetSeconds, double altitudeFt, double groundspeedKt ) {
        return PilotTrackPoint.builder()
            .recordedAt( LOGON.plusSeconds( offsetSeconds ) )
            .cid( 123456L )
            .callsign( "DLH400" )
            .logonTime( LOGON )
            .latitude( 50.0 )
            .longitude( 8.5 )
            .altitudeFt( (int) altitudeFt )
            .groundspeedKt( (int) groundspeedKt )
            .build();
    }
}
