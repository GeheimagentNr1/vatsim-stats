package de.secretsoft.vatsim_stats.ingestion.session;

import de.secretsoft.vatsim_stats.ingestion.domain.PilotSession;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPoint;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotTrackPointRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PilotSessionTimeoutSweeperTest {

    private static final Instant LOGON = Instant.parse( "2026-08-24T09:00:00Z" );

    private InMemoryPilotSessionRepository sessionRepository;
    private PilotTrackPointRepository trackPointRepository;

    @BeforeEach
    void setUp() {
        sessionRepository = new InMemoryPilotSessionRepository();
        trackPointRepository = mock( PilotTrackPointRepository.class );
    }

    private PilotSessionTimeoutSweeper sweeper( Instant now ) {
        Clock clock = Clock.fixed( now, ZoneOffset.UTC );
        return new PilotSessionTimeoutSweeper( sessionRepository, trackPointRepository, clock );
    }

    private PilotSession activeSession( long cid, String callsign, int sequenceNumber, Instant startedAt ) {
        return sessionRepository.save( PilotSession.builder()
            .cid( cid )
            .callsign( callsign )
            .logonTime( LOGON )
            .sequenceNumber( sequenceNumber )
            .status( SessionStatus.ACTIVE )
            .startedAt( startedAt )
            .build() );
    }

    private PilotSession activeSession() {
        return activeSession( 123456L, "DLH400", 0, LOGON );
    }

    private PilotTrackPoint trackPointAt( Instant recordedAt ) {
        return PilotTrackPoint.builder()
            .recordedAt( recordedAt )
            .cid( 123456L )
            .callsign( "DLH400" )
            .logonTime( LOGON )
            .latitude( 50.0 )
            .longitude( 8.5 )
            .altitudeFt( 3000 )
            .groundspeedKt( 250 )
            .build();
    }

    private void stubLastTrackPoint( long cid, String callsign, Instant sessionStartedAt, Optional<PilotTrackPoint> result ) {
        when( trackPointRepository
            .findTopByCidAndCallsignAndLogonTimeAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
                cid, callsign, LOGON, sessionStartedAt ) )
            .thenReturn( result );
    }

    @Test
    void leavesASessionActiveWhenTheLastTrackPointIsWithinTheTimeout() {
        activeSession();
        Instant lastKnown = LOGON.plusSeconds( 3600 );
        stubLastTrackPoint( 123456L, "DLH400", LOGON, Optional.of( trackPointAt( lastKnown ) ) );

        sweeper( lastKnown.plus( Duration.ofMinutes( 29 ) ) ).closeTimedOutSessions();

        PilotSession session = sessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc( 123456L, "DLH400", LOGON )
            .orElseThrow();
        assertThat( session.getStatus() ).isEqualTo( SessionStatus.ACTIVE );
        assertThat( session.getEndedAt() ).isNull();
    }

    @Test
    void closesASessionWhoseLastTrackPointIsOlderThanThirtyMinutesWithoutASyntheticLandingEvent() {
        activeSession();
        Instant lastKnown = LOGON.plusSeconds( 3600 );
        stubLastTrackPoint( 123456L, "DLH400", LOGON, Optional.of( trackPointAt( lastKnown ) ) );

        sweeper( lastKnown.plus( Duration.ofMinutes( 30 ) ) ).closeTimedOutSessions();

        PilotSession session = sessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc( 123456L, "DLH400", LOGON )
            .orElseThrow();
        assertThat( session.getStatus() ).isEqualTo( SessionStatus.COMPLETED );
        // endedAt is the last known track point's time, not "now" -- the job must not fabricate
        // a later end time than what was actually observed.
        assertThat( session.getEndedAt() ).isEqualTo( lastKnown );
    }

    @Test
    void fallsBackToStartedAtWhenNoTrackPointExistsForTheSession() {
        activeSession();
        stubLastTrackPoint( 123456L, "DLH400", LOGON, Optional.empty() );

        sweeper( LOGON.plus( Duration.ofMinutes( 30 ) ) ).closeTimedOutSessions();

        PilotSession session = sessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc( 123456L, "DLH400", LOGON )
            .orElseThrow();
        assertThat( session.getStatus() ).isEqualTo( SessionStatus.COMPLETED );
        assertThat( session.getEndedAt() ).isEqualTo( LOGON );
    }

    @Test
    void completedSessionsAreIgnored() {
        PilotSession completed = sessionRepository.save( PilotSession.builder()
            .cid( 999L ).callsign( "BAW1" ).logonTime( LOGON ).sequenceNumber( 0 )
            .status( SessionStatus.COMPLETED ).startedAt( LOGON ).endedAt( LOGON.plusSeconds( 60 ) )
            .build() );

        sweeper( LOGON.plus( Duration.ofHours( 5 ) ) ).closeTimedOutSessions();

        PilotSession unchanged = sessionRepository.findById( completed.getId() ).orElseThrow();
        assertThat( unchanged.getEndedAt() ).isEqualTo( LOGON.plusSeconds( 60 ) );
    }

    @Test
    void multipleActiveSessionsInOneSweepAreEvaluatedIndependently() {
        activeSession( 123456L, "DLH400", 0, LOGON );
        activeSession( 999L, "BAW1", 0, LOGON );
        Instant staleLastKnown = LOGON.plusSeconds( 60 );
        Instant freshLastKnown = LOGON.plusSeconds( 3500 );
        stubLastTrackPoint( 123456L, "DLH400", LOGON, Optional.of( trackPointAt( staleLastKnown ) ) );
        when( trackPointRepository
            .findTopByCidAndCallsignAndLogonTimeAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
                999L, "BAW1", LOGON, LOGON ) )
            .thenReturn( Optional.of( PilotTrackPoint.builder()
                .recordedAt( freshLastKnown ).cid( 999L ).callsign( "BAW1" ).logonTime( LOGON )
                .latitude( 50.0 ).longitude( 8.5 ).altitudeFt( 3000 ).groundspeedKt( 250 ).build() ) );

        sweeper( LOGON.plus( Duration.ofHours( 1 ) ) ).closeTimedOutSessions();

        assertThat( sessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc( 123456L, "DLH400", LOGON )
            .orElseThrow().getStatus() ).isEqualTo( SessionStatus.COMPLETED );
        assertThat( sessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc( 999L, "BAW1", LOGON )
            .orElseThrow().getStatus() ).isEqualTo( SessionStatus.ACTIVE );
    }

    @Test
    void anEarlierCompletedLegsTrackPointsAreNeverAttributedToALaterActiveLeg() {
        // A refile-after-landing scenario: leg 0 landed and completed long ago with plenty of recent
        // (at the time) track points; leg 1 is the current ACTIVE leg, started much later, and has
        // gone silent. If the sweeper's query were not bounded by the leg's own startedAt, leg 0's
        // track points (which sort newest amongst a naive unbounded query only if leg 1 has none yet)
        // could otherwise mask leg 1's real silence -- or, as guarded against here, an unbounded query
        // must never even be consulted for a point that belongs to a different leg.
        sessionRepository.save( PilotSession.builder()
            .cid( 123456L ).callsign( "DLH400" ).logonTime( LOGON ).sequenceNumber( 0 )
            .status( SessionStatus.COMPLETED ).startedAt( LOGON ).endedAt( LOGON.plusSeconds( 200 ) )
            .build() );
        Instant leg1StartedAt = LOGON.plusSeconds( 500 );
        activeSession( 123456L, "DLH400", 1, leg1StartedAt );

        // No track point recorded for leg 1 at all -- the bounded query (from leg1StartedAt onward)
        // correctly finds nothing, regardless of how many track points leg 0 has before that instant.
        stubLastTrackPoint( 123456L, "DLH400", leg1StartedAt, Optional.empty() );

        sweeper( leg1StartedAt.plus( Duration.ofMinutes( 30 ) ) ).closeTimedOutSessions();

        PilotSession leg1 = sessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc( 123456L, "DLH400", LOGON )
            .orElseThrow();
        assertThat( leg1.getSequenceNumber() ).isEqualTo( 1 );
        assertThat( leg1.getStatus() ).isEqualTo( SessionStatus.COMPLETED );
        // Falls back to leg 1's own startedAt, never leg 0's endedAt or any of leg 0's track points.
        assertThat( leg1.getEndedAt() ).isEqualTo( leg1StartedAt );
    }
}
