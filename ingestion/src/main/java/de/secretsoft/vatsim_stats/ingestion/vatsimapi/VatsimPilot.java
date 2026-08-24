package de.secretsoft.vatsim_stats.ingestion.vatsimapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties( ignoreUnknown = true )
public record VatsimPilot(
    long cid,
    String callsign,
    double latitude,
    double longitude,
    int altitude,
    int groundspeed,
    Integer heading,
    String transponder,
    @JsonProperty( "qnh_mb" ) Integer qnhMb,
    @JsonProperty( "logon_time" ) Instant logonTime,
    @JsonProperty( "flight_plan" ) VatsimFlightPlan flightPlan ) {
}
