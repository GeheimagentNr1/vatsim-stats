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
 * ~1 minute),
 * so this job only ever reconciles database rows and never interferes with live processing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PilotSessionTimeoutSweeper {

    private static final Duration TIMEOUT = Duration.ofMinutes( 30 );

    private final PilotSessionRepository pilotSessionRepository;
    private final PilotTrackPointRepository pilotTrackPointRepository;
    private final Clock clock;

    @Scheduled( fixedRate = 300000 )
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
        }
        if( closed > 0 ) {
            log.info( "Closed {} pilot session(s) with no track point in the last {}", closed, TIMEOUT );
        }
    }

    private Instant lastKnownActivity( PilotSession session ) {
        return pilotTrackPointRepository
            .findTopByCidAndCallsignAndLogonTimeOrderByRecordedAtDesc(
                session.getCid(), session.getCallsign(), session.getLogonTime() )
            .map( PilotTrackPoint::getRecordedAt )
            .orElse( session.getStartedAt() );
    }
}
