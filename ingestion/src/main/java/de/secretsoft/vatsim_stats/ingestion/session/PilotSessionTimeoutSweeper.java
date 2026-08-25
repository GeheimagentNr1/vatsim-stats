package de.secretsoft.vatsim_stats.ingestion.session;

import de.secretsoft.vatsim_stats.ingestion.domain.PilotSession;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotSessionRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPoint;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPointRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.SessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Closes {@link PilotSession}s that have gone silent for too long to ever be confirmed as landed.
 * A pilot who disconnects mid-flight (never reaching {@code GROUND_PENDING}) produces no LANDING
 * event by design (see the phase detection spec) — fabricating one would record a landing at an
 * airport that was never actually reached. Without this sweep such a session would stay
 * {@code ACTIVE} forever. By the time the timeout below elapses, the pilot has long since been
 * evicted from {@link PilotSessionOrchestrator}'s in-memory state (after 4 missed poll cycles,
 * ~1 minute at the default 15s poll interval), so this job only ever reconciles database rows and
 * never interferes with live processing. This assumes {@code closeTimedOutSessions()} never runs
 * concurrently with itself or with {@link PilotSessionOrchestrator#processTrackPoints}, which holds
 * as long as Spring's scheduler pool stays single-threaded (see {@code application.yml}'s
 * {@code spring.task.scheduling.pool.size: 1}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PilotSessionTimeoutSweeper {

    private static final Duration TIMEOUT = Duration.ofMinutes( 30 );

    private final PilotSessionRepository pilotSessionRepository;
    private final PilotTrackPointRepository pilotTrackPointRepository;
    private final Clock clock;

    @Scheduled( fixedRateString = "PT5M" )
    @Transactional
    public void closeTimedOutSessions() {
        Instant now = clock.instant();
        int closed = 0;
        for( PilotSession session : pilotSessionRepository.findByStatus( SessionStatus.ACTIVE ) ) {
            Instant lastKnownActivity = lastKnownActivity( session );
            if( Duration.between( lastKnownActivity, now ).compareTo( TIMEOUT ) < 0 ) {
                continue;
            }
            session.setStatus( SessionStatus.COMPLETED );
            session.setEndedAt( lastKnownActivity );
            pilotSessionRepository.save( session );
            closed++;
            log.debug( "Closed timed-out pilot session cid={} callsign={} logonTime={} sequenceNumber={} endedAt={}",
                session.getCid(), session.getCallsign(), session.getLogonTime(),
                session.getSequenceNumber(), lastKnownActivity );
        }
        if( closed > 0 ) {
            log.info( "Closed {} pilot session(s) with no track point in the last {}", closed, TIMEOUT );
        }
    }

    /**
     * The last known activity for this specific leg, bounded to track points recorded at or after
     * the leg's own {@code startedAt} — see {@link PilotTrackPointRepository
     * #findTopByCidAndCallsignAndLogonTimeAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc} for why
     * the bound is required whenever multiple legs share one (cid, callsign, logonTime) natural key.
     */
    private Instant lastKnownActivity( PilotSession session ) {
        return pilotTrackPointRepository
            .findTopByCidAndCallsignAndLogonTimeAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
                session.getCid(), session.getCallsign(), session.getLogonTime(), session.getStartedAt() )
            .map( PilotTrackPoint::getRecordedAt )
            .orElse( session.getStartedAt() );
    }
}
