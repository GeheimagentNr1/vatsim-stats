package de.secretsoft.vatsim_stats.detection;

import java.time.Instant;

public record AirportEvent( String airportIcao, AirportEventType type, Instant timestamp ) {
}
