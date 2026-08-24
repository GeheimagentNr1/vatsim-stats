package de.secretsoft.vatsim_stats.detection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HaversineTest {

    @Test
    void distanceBetweenFrankfurtAndMunichIsAbout162Nm() {
        double distance = Haversine.distanceNm( 50.026421, 8.543125, 48.353783, 11.786086 );

        assertThat( distance ).isCloseTo( 162.2, within( 2.0 ) );
    }

    @Test
    void distanceToSelfIsZero() {
        double distance = Haversine.distanceNm( 50.026421, 8.543125, 50.026421, 8.543125 );

        assertThat( distance ).isCloseTo( 0.0, within( 0.0001 ) );
    }
}
