package de.secretsoft.vatsim_stats.ingestion.vatsimapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties( ignoreUnknown = true )
public record VatsimFlightPlan(
    String departure,
    String arrival,
    @JsonProperty( "aircraft_short" ) String aircraftShort ) {
}
