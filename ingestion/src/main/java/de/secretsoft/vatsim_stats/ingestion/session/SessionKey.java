package de.secretsoft.vatsim_stats.ingestion.session;

import java.time.Instant;

public record SessionKey( long cid, String callsign, Instant logonTime ) {
}
