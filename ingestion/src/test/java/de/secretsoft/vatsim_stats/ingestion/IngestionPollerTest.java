package de.secretsoft.vatsim_stats.ingestion;

import de.secretsoft.vatsim_stats.ingestion.domain.AtcSnapshot;
import de.secretsoft.vatsim_stats.ingestion.domain.AtcSnapshotRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPoint;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPointRepository;
import de.secretsoft.vatsim_stats.ingestion.session.PilotSessionOrchestrator;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimController;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimDataFeed;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimDataFeedClient;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimFeedException;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimFlightPlan;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimPilot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionPollerTest {

    private VatsimDataFeedClient feedClient;
    private PilotTrackPointRepository trackPointRepository;
    private AtcSnapshotRepository atcSnapshotRepository;
    private PilotSessionOrchestrator sessionOrchestrator;
    private IngestionPoller poller;

    @BeforeEach
    void setUp() {
        feedClient = mock( VatsimDataFeedClient.class );
        trackPointRepository = mock( PilotTrackPointRepository.class );
        atcSnapshotRepository = mock( AtcSnapshotRepository.class );
        sessionOrchestrator = mock( PilotSessionOrchestrator.class );
        poller = new IngestionPoller( feedClient, trackPointRepository, atcSnapshotRepository, sessionOrchestrator );
    }

    @Test
    void savesAllValidPilotsAndControllersFromOneCycle() {
        VatsimPilot pilot = new VatsimPilot(
            123456L, "DLH400", 50.0264, 8.5431, 3000, 180, 270, "2000", 1013,
            Instant.parse( "2026-08-24T09:45:00Z" ),
            new VatsimFlightPlan( "EDDF", "EDDM", "A320" ) );
        VatsimController controller = new VatsimController(
            111222L, "EDDF_TWR", "119.900", 4, 50, null, null,
            Instant.parse( "2026-08-24T09:00:00Z" ) );
        when( feedClient.fetchCurrent() ).thenReturn( new VatsimDataFeed( List.of( pilot ), List.of( controller ) ) );

        PollResult result = poller.pollOnce();

        assertThat( result.trackPointsSaved() ).isEqualTo( 1 );
        assertThat( result.atcSnapshotsSaved() ).isEqualTo( 1 );
        assertThat( result.recordsSkipped() ).isEqualTo( 0 );

        ArgumentCaptor<List<PilotTrackPoint>> trackPointsCaptor = ArgumentCaptor.forClass( List.class );
        verify( trackPointRepository ).saveAll( trackPointsCaptor.capture() );
        PilotTrackPoint saved = trackPointsCaptor.getValue().get( 0 );
        assertThat( saved.getCid() ).isEqualTo( 123456L );
        assertThat( saved.getCallsign() ).isEqualTo( "DLH400" );
        assertThat( saved.getFlightPlanDeparture() ).isEqualTo( "EDDF" );
        assertThat( saved.getAircraftShort() ).isEqualTo( "A320" );

        ArgumentCaptor<List<AtcSnapshot>> atcCaptor = ArgumentCaptor.forClass( List.class );
        verify( atcSnapshotRepository ).saveAll( atcCaptor.capture() );
        assertThat( atcCaptor.getValue().get( 0 ).getCallsign() ).isEqualTo( "EDDF_TWR" );

        verify( sessionOrchestrator ).processTrackPoints( trackPointsCaptor.getValue() );
    }

    @Test
    void skipsAPilotWithABlankCallsignWithoutFailingTheWholeCycle() {
        VatsimPilot valid = new VatsimPilot(
            1L, "DLH400", 50.0, 8.5, 3000, 180, 270, "2000", 1013, Instant.now(), null );
        VatsimPilot invalid = new VatsimPilot(
            2L, "  ", 50.0, 8.5, 3000, 180, 270, "2000", 1013, Instant.now(), null );
        when( feedClient.fetchCurrent() ).thenReturn( new VatsimDataFeed( List.of( valid, invalid ), List.of() ) );

        PollResult result = poller.pollOnce();

        assertThat( result.trackPointsSaved() ).isEqualTo( 1 );
        assertThat( result.recordsSkipped() ).isEqualTo( 1 );
    }

    @Test
    void returnsAnEmptyResultWithoutThrowingWhenTheFeedFails() {
        when( feedClient.fetchCurrent() ).thenThrow( new VatsimFeedException( "boom", null ) );

        PollResult result = poller.pollOnce();

        assertThat( result.trackPointsSaved() ).isZero();
        assertThat( result.atcSnapshotsSaved() ).isZero();
        assertThat( result.recordsSkipped() ).isZero();
    }
}
