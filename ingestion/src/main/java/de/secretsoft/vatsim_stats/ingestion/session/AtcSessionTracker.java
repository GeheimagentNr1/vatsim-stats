package de.secretsoft.vatsim_stats.ingestion.session;

import de.secretsoft.vatsim_stats.ingestion.domain.AtcSession;
import de.secretsoft.vatsim_stats.ingestion.domain.AtcSessionRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.AtcSnapshot;
import de.secretsoft.vatsim_stats.ingestion.domain.AtcSnapshotRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
public class AtcSessionTracker {

    private final AtcSessionRepository atcSessionRepository;
    private final AtcSnapshotRepository atcSnapshotRepository;

    private final ConcurrentMap<SessionKey, AtcSession> openSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<SessionKey, Instant> lastSeenAt = new ConcurrentHashMap<>();

    @PostConstruct
    void reconstructOpenSessions() {
        for( AtcSession session : atcSessionRepository.findByEndedAtIsNull() ) {
            SessionKey key = new SessionKey( session.getCid(), session.getCallsign(), session.getLogonTime() );
            openSessions.put( key, session );
            // Seed "last seen" from the newest persisted raw snapshot, not from startedAt: a
            // controller who was online for hours before a restart and logs off during the downtime
            // must be closed with their real last-seen time, not truncated to a 0-duration session.
            Instant lastSnapshotAt = atcSnapshotRepository
                .findTopByCidAndCallsignAndLogonTimeOrderByRecordedAtDesc(
                    session.getCid(), session.getCallsign(), session.getLogonTime() )
                .map( AtcSnapshot::getRecordedAt )
                .orElse( session.getStartedAt() );
            lastSeenAt.put( key, lastSnapshotAt );
        }
    }

    @Transactional
    public void processSnapshots( List<AtcSnapshot> snapshots ) {
        Set<SessionKey> seenThisCycle = new HashSet<>();
        for( AtcSnapshot snapshot : snapshots ) {
            SessionKey key = new SessionKey( snapshot.getCid(), snapshot.getCallsign(), snapshot.getLogonTime() );
            seenThisCycle.add( key );
            lastSeenAt.put( key, snapshot.getRecordedAt() );
            openSessions.computeIfAbsent( key, k -> createSession( k, snapshot ) );
        }
        closeSessionsNotSeen( seenThisCycle );
    }

    /**
     * Returns the session for {@code key}, reusing the persisted row if one already exists. A
     * controller who misses a single feed cycle is closed by {@link #closeSessionsNotSeen} and then
     * reappears with the identical (cid, callsign, logonTime); inserting a second row would violate
     * the {@code UNIQUE (cid, callsign, logon_time)} constraint and abort the whole poll cycle, so
     * the existing row is reopened instead.
     */
    private AtcSession createSession( SessionKey key, AtcSnapshot snapshot ) {
        AtcSession existing = atcSessionRepository
            .findByCidAndCallsignAndLogonTime( key.cid(), key.callsign(), key.logonTime() )
            .orElse( null );
        if( existing != null ) {
            existing.setEndedAt( null );
            return atcSessionRepository.save( existing );
        }

        AtcSession session = AtcSession.builder()
            .cid( key.cid() )
            .callsign( key.callsign() )
            .logonTime( key.logonTime() )
            .facility( snapshot.getFacility() )
            .startedAt( snapshot.getRecordedAt() )
            .build();
        return atcSessionRepository.save( session );
    }

    private void closeSessionsNotSeen( Set<SessionKey> seenThisCycle ) {
        for( SessionKey key : Set.copyOf( openSessions.keySet() ) ) {
            if( !seenThisCycle.contains( key ) ) {
                AtcSession session = openSessions.get( key );
                session.setEndedAt( lastSeenAt.get( key ) );
                atcSessionRepository.save( session );
                openSessions.remove( key );
            }
        }
    }
}
