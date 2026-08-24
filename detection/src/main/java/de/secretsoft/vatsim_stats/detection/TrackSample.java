package de.secretsoft.vatsim_stats.detection;

import java.time.Instant;

public record TrackSample(
    Instant timestamp,
    double latitude,
    double longitude,
    double altitudeFt,
    double groundspeedKt ) {
}
