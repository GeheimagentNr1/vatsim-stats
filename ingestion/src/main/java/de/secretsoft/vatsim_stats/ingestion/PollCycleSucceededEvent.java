package de.secretsoft.vatsim_stats.ingestion;

import java.time.Instant;

public record PollCycleSucceededEvent( Instant occurredAt ) {
}
