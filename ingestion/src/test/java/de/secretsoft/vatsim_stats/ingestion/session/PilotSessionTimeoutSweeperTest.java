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

    private PilotSession activeSession() {
        return sessionRepository.save( PilotSession.builder()
            .cid( 123456L )
            .callsign( "DLH400" )
            .logonTime( LOGON )
            .sequenceNumber( 0 )
            .status( SessionStatus.ACTIVE )
            .startedAt( LOGON )
            .build() );
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

    @Test
    void leavesASessionActiveWhenTheLastTrackPointIsWithinTheTimeout() {
        activeSession();
        Instant lastKnown = LOGON.plusSeconds( 3600 );
        when( trackPointRepository.findTopByCidAndCallsignAndLogonTimeOrderByRecordedAtDesc( 123456L, "DLH400", LOGON ) )
            .thenReturn( Optional.of( trackPointAt( lastKnown ) ) );

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
        when( trackPointRepository.findTopByCidAndCallsignAndLogonTimeOrderByRecordedAtDesc( 123456L, "DLH400", LOGON ) )
            .thenReturn( Optional.of( trackPointAt( lastKnown ) ) );

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
        when( trackPointRepository.findTopByCidAndCallsignAndLogonTimeOrderByRecordedAtDesc( 123456L, "DLH400", LOGON ) )
            .thenReturn( Optional.empty() );

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
}
