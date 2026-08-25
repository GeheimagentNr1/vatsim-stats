package de.secretsoft.vatsim_stats.ingestion.session;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DisappearanceDebounceTest {

    private final DisappearanceDebounce<String> debounce = new DisappearanceDebounce<>( 4 );

    @Test
    void doesNotReportThresholdReachedBelowTheLimit() {
        assertThat( debounce.recordMissAndCheckThresholdReached( "A" ) ).isFalse();
        assertThat( debounce.recordMissAndCheckThresholdReached( "A" ) ).isFalse();
        assertThat( debounce.recordMissAndCheckThresholdReached( "A" ) ).isFalse();
    }

    @Test
    void reportsThresholdReachedOnTheFourthConsecutiveMiss() {
        debounce.recordMissAndCheckThresholdReached( "A" );
        debounce.recordMissAndCheckThresholdReached( "A" );
        debounce.recordMissAndCheckThresholdReached( "A" );

        assertThat( debounce.recordMissAndCheckThresholdReached( "A" ) ).isTrue();
    }

    @Test
    void seenResetsTheCounterSoAFreshRunOfMissesIsRequiredAfterwards() {
        debounce.recordMissAndCheckThresholdReached( "A" );
        debounce.recordMissAndCheckThresholdReached( "A" );
        debounce.recordMissAndCheckThresholdReached( "A" );

        debounce.seen( "A" );

        assertThat( debounce.recordMissAndCheckThresholdReached( "A" ) ).isFalse();
        assertThat( debounce.recordMissAndCheckThresholdReached( "A" ) ).isFalse();
        assertThat( debounce.recordMissAndCheckThresholdReached( "A" ) ).isFalse();
        assertThat( debounce.recordMissAndCheckThresholdReached( "A" ) ).isTrue();
    }

    @Test
    void reachingTheThresholdResetsTheCounterSoANewRunStartsFromZero() {
        for( int i = 0; i < 3; i++ ) {
            debounce.recordMissAndCheckThresholdReached( "A" );
        }
        assertThat( debounce.recordMissAndCheckThresholdReached( "A" ) ).isTrue();

        assertThat( debounce.recordMissAndCheckThresholdReached( "A" ) ).isFalse();
        assertThat( debounce.recordMissAndCheckThresholdReached( "A" ) ).isFalse();
        assertThat( debounce.recordMissAndCheckThresholdReached( "A" ) ).isFalse();
        assertThat( debounce.recordMissAndCheckThresholdReached( "A" ) ).isTrue();
    }

    @Test
    void keysAreTrackedIndependently() {
        debounce.recordMissAndCheckThresholdReached( "A" );
        debounce.recordMissAndCheckThresholdReached( "A" );
        debounce.recordMissAndCheckThresholdReached( "A" );

        assertThat( debounce.recordMissAndCheckThresholdReached( "B" ) ).isFalse();
        assertThat( debounce.recordMissAndCheckThresholdReached( "A" ) ).isTrue();
    }
}
