package de.secretsoft.vatsim_stats.detection;

import java.time.Instant;

public record PhaseSnapshot(
    Phase phase,
    String pendingAirportIcao,
    Instant pendingSince,
    boolean pendingTouchedDown,
    String groundAirportIcao ) {
}
