package de.secretsoft.vatsim_stats.ingestion.session;

import de.secretsoft.vatsim_stats.ingestion.domain.AtcSession;
import de.secretsoft.vatsim_stats.ingestion.domain.AtcSessionRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.AtcSnapshot;
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

    private final ConcurrentMap<SessionKey, AtcSession> openSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<SessionKey, Instant> lastSeenAt = new ConcurrentHashMap<>();

    @PostConstruct
    void reconstructOpenSessions() {
        for( AtcSession session : atcSessionRepository.findByEndedAtIsNull() ) {
            SessionKey key = new SessionKey( session.getCid(), session.getCallsign(), session.getLogonTime() );
            openSessions.put( key, session );
            lastSeenAt.put( key, session.getStartedAt() );
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

    private AtcSession createSession( SessionKey key, AtcSnapshot snapshot ) {
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
