package de.secretsoft.vatsim_stats.detection;

import java.time.Duration;

public record PhaseDetectionConfig(
    double groundspeedThresholdKt,
    double altitudeAglThresholdFt,
    double nearestAirportRadiusNm,
    Duration groundDwellThreshold ) {

    public static PhaseDetectionConfig defaults() {
        return new PhaseDetectionConfig( 40.0, 200.0, 5.0, Duration.ofSeconds( 90 ) );
    }
}
