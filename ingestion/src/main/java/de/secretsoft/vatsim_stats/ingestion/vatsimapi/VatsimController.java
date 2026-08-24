package de.secretsoft.vatsim_stats.ingestion.vatsimapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties( ignoreUnknown = true )
public record VatsimController(
    long cid,
    String callsign,
    String frequency,
    Integer facility,
    @JsonProperty( "visual_range" ) Integer visualRange,
    Double latitude,
    Double longitude,
    @JsonProperty( "logon_time" ) Instant logonTime ) {
}
