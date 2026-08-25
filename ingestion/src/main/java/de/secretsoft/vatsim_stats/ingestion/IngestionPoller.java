package de.secretsoft.vatsim_stats.ingestion;

import de.secretsoft.vatsim_stats.ingestion.domain.AtcSnapshot;
import de.secretsoft.vatsim_stats.ingestion.domain.AtcSnapshotRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPoint;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPointRepository;
import de.secretsoft.vatsim_stats.ingestion.session.AtcSessionTracker;
import de.secretsoft.vatsim_stats.ingestion.session.PilotSessionOrchestrator;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimController;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimDataFeed;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimDataFeedClient;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimFlightPlan;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimPilot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
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
    private final AtcSessionTracker atcSessionTracker;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Scheduled( fixedRateString = "${vatsim.poll-interval-ms:15000}" )
    public void poll() {
        pollOnce();
    }

    /**
     * Runs a single poll cycle.
     * <p>
     * Deliberately <strong>not</strong> {@code @Transactional}: the scheduled {@link #poll()} entry
     * point invokes this method on {@code this}, which Spring's AOP proxy never intercepts, so an
     * annotation here would silently have no effect in production. Instead the transaction
     * boundaries live where they are genuinely proxied and where the spec wants them:
     * {@code saveAll(..)} on the raw-data repositories is transactional in Spring Data's
     * {@code SimpleJpaRepository}, and {@code PilotSessionOrchestrator#processTrackPoints} /
     * {@code AtcSessionTracker#processSnapshots} carry their own {@code @Transactional} on separate
     * beans. That also gives the ordering the design demands: raw data is committed <em>before</em>
     * any derivation runs, so a failure in the derivation logic can never roll back raw data.
     */
    public PollResult pollOnce() {
        VatsimDataFeed feed;
        try {
            feed = feedClient.fetchCurrent();
        } catch( Exception e ) {
            log.warn( "Skipping poll cycle: failed to fetch VATSIM data feed", e );
            return PollResult.EMPTY;
        }

        Instant recordedAt = clock.instant();
        int skipped = 0;

        List<PilotTrackPoint> trackPoints = new ArrayList<>();
        for( VatsimPilot pilot : feed.pilots() ) {
            if( pilot.callsign() == null || pilot.callsign().isBlank() ) {
                log.debug( "Skipping pilot record without callsign (cid={})", pilot.cid() );
                skipped++;
                continue;
            }
            if( pilot.logonTime() == null ) {
                log.debug( "Skipping pilot record without logon_time (callsign={})", pilot.callsign() );
                skipped++;
                continue;
            }
            if( isImplausiblePosition( pilot.latitude(), pilot.longitude() ) ) {
                log.debug( "Skipping pilot record without a usable position (callsign={})", pilot.callsign() );
                skipped++;
                continue;
            }
            trackPoints.add( toTrackPoint( pilot, recordedAt ) );
        }

        List<AtcSnapshot> atcSnapshots = new ArrayList<>();
        for( VatsimController controller : feed.controllers() ) {
            if( controller.callsign() == null || controller.callsign().isBlank() ) {
                log.debug( "Skipping controller record without callsign (cid={})", controller.cid() );
                skipped++;
                continue;
            }
            if( controller.logonTime() == null ) {
                log.debug( "Skipping controller record without logon_time (callsign={})", controller.callsign() );
                skipped++;
                continue;
            }
            atcSnapshots.add( toAtcSnapshot( controller, recordedAt ) );
        }

        try {
            if( !trackPoints.isEmpty() ) {
                trackPointRepository.saveAll( trackPoints );
            }
            if( !atcSnapshots.isEmpty() ) {
                atcSnapshotRepository.saveAll( atcSnapshots );
            }

            // Always invoked, even with an empty list: both components use the call to detect which
            // participants disappeared from the feed and must have their sessions closed.
            sessionOrchestrator.processTrackPoints( trackPoints );
            atcSessionTracker.processSnapshots( atcSnapshots );
        } catch( Exception e ) {
            // Per the design spec, a transient persistence failure ("DB kurzzeitig nicht erreichbar")
            // logs and skips the cycle rather than propagating into the scheduler.
            log.error( "Skipping poll cycle: failed to persist or process the fetched feed data", e );
            return PollResult.EMPTY;
        }

        eventPublisher.publishEvent( new PollCycleSucceededEvent( recordedAt ) );
        return new PollResult( trackPoints.size(), atcSnapshots.size(), skipped );
    }

    /**
     * VATSIM's feed maps a missing {@code latitude}/{@code longitude} to the {@code double} default
     * {@code 0.0}, which is indistinguishable from Null Island. No real VATSIM pilot sits at exactly
     * 0/0, so treating that pair as "position missing" is the safe reading.
     */
    private boolean isImplausiblePosition( double latitude, double longitude ) {
        return latitude == 0.0 && longitude == 0.0;
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
