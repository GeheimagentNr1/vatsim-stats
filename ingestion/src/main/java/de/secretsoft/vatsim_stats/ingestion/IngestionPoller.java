package de.secretsoft.vatsim_stats.ingestion;

import de.secretsoft.vatsim_stats.ingestion.domain.AtcSnapshot;
import de.secretsoft.vatsim_stats.ingestion.domain.AtcSnapshotRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPoint;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPointRepository;
import de.secretsoft.vatsim_stats.ingestion.session.PilotSessionOrchestrator;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimController;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimDataFeed;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimDataFeedClient;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimFlightPlan;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimPilot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionPoller {

    private final VatsimDataFeedClient feedClient;
    private final PilotTrackPointRepository trackPointRepository;
    private final AtcSnapshotRepository atcSnapshotRepository;
    private final PilotSessionOrchestrator sessionOrchestrator;

    @Scheduled( fixedRateString = "${vatsim.poll-interval-ms:15000}" )
    public void poll() {
        pollOnce();
    }

    @Transactional
    public PollResult pollOnce() {
        VatsimDataFeed feed;
        try {
            feed = feedClient.fetchCurrent();
        } catch( Exception e ) {
            log.warn( "Skipping poll cycle: failed to fetch VATSIM data feed", e );
            return PollResult.EMPTY;
        }

        Instant recordedAt = Instant.now();
        int skipped = 0;

        List<PilotTrackPoint> trackPoints = new ArrayList<>();
        for( VatsimPilot pilot : feed.pilots() ) {
            if( pilot.callsign() == null || pilot.callsign().isBlank() ) {
                skipped++;
                continue;
            }
            trackPoints.add( toTrackPoint( pilot, recordedAt ) );
        }

        List<AtcSnapshot> atcSnapshots = new ArrayList<>();
        for( VatsimController controller : feed.controllers() ) {
            if( controller.callsign() == null || controller.callsign().isBlank() ) {
                skipped++;
                continue;
            }
            atcSnapshots.add( toAtcSnapshot( controller, recordedAt ) );
        }

        if( !trackPoints.isEmpty() ) {
            trackPointRepository.saveAll( trackPoints );
        }
        if( !atcSnapshots.isEmpty() ) {
            atcSnapshotRepository.saveAll( atcSnapshots );
        }

        if( !trackPoints.isEmpty() ) {
            sessionOrchestrator.processTrackPoints( trackPoints );
        }

        return new PollResult( trackPoints.size(), atcSnapshots.size(), skipped );
    }

    private PilotTrackPoint toTrackPoint( VatsimPilot pilot, Instant recordedAt ) {
        VatsimFlightPlan flightPlan = pilot.flightPlan();
        return PilotTrackPoint.builder()
            .recordedAt( recordedAt )
            .cid( pilot.cid() )
            .callsign( pilot.callsign() )
            .logonTime( pilot.logonTime() )
            .latitude( pilot.latitude() )
            .longitude( pilot.longitude() )
            .altitudeFt( pilot.altitude() )
            .groundspeedKt( pilot.groundspeed() )
            .heading( pilot.heading() )
            .transponder( pilot.transponder() )
            .qnhMb( pilot.qnhMb() )
            .flightPlanDeparture( flightPlan != null ? flightPlan.departure() : null )
            .flightPlanDestination( flightPlan != null ? flightPlan.arrival() : null )
            .aircraftShort( flightPlan != null ? flightPlan.aircraftShort() : null )
            .build();
    }

    private AtcSnapshot toAtcSnapshot( VatsimController controller, Instant recordedAt ) {
        return AtcSnapshot.builder()
            .recordedAt( recordedAt )
            .cid( controller.cid() )
            .callsign( controller.callsign() )
            .logonTime( controller.logonTime() )
            .frequency( controller.frequency() )
            .facility( controller.facility() )
            .visualRange( controller.visualRange() )
            .latitude( controller.latitude() )
            .longitude( controller.longitude() )
            .build();
    }
}
