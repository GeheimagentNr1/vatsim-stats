package de.secretsoft.vatsim_stats.ingestion.vatsimapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties( ignoreUnknown = true )
public record VatsimDataFeed( List<VatsimPilot> pilots, List<VatsimController> controllers ) {
}
