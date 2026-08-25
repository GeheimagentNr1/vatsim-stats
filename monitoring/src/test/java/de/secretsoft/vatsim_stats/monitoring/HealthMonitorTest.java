package de.secretsoft.vatsim_stats.monitoring;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class HealthMonitorTest {

    private static final Duration THRESHOLD = Duration.ofMinutes( 5 );

    @Test
    void isOverdueWhenNoSuccessWasEverRecorded() {
        HealthMonitor monitor = new HealthMonitor();

        assertThat( monitor.isOverdue( "vatsim-poll", THRESHOLD, Instant.now() ) ).isTrue();
    }

    @Test
    void isNotOverdueRightAfterASuccess() {
        HealthMonitor monitor = new HealthMonitor();
        Instant now = Instant.parse( "2026-08-24T10:00:00Z" );
        monitor.recordSuccess( "vatsim-poll", now );

        assertThat( monitor.isOverdue( "vatsim-poll", THRESHOLD, now.plus( Duration.ofMinutes( 2 ) ) ) ).isFalse();
    }

    @Test
    void isOverdueOnceThresholdElapsesSinceLastSuccess() {
        HealthMonitor monitor = new HealthMonitor();
        Instant now = Instant.parse( "2026-08-24T10:00:00Z" );
        monitor.recordSuccess( "vatsim-poll", now );

        assertThat( monitor.isOverdue( "vatsim-poll", THRESHOLD, now.plus( Duration.ofMinutes( 6 ) ) ) ).isTrue();
    }

    @Test
    void alertLifecycleTracksMarkedAndClearedState() {
        HealthMonitor monitor = new HealthMonitor();

        assertThat( monitor.isAlerted( "vatsim-poll" ) ).isFalse();
        monitor.markAlerted( "vatsim-poll" );
        assertThat( monitor.isAlerted( "vatsim-poll" ) ).isTrue();
        monitor.clearAlert( "vatsim-poll" );
        assertThat( monitor.isAlerted( "vatsim-poll" ) ).isFalse();
    }
}
