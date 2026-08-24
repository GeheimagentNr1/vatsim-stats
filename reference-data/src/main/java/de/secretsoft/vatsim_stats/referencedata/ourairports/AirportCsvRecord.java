package de.secretsoft.vatsim_stats.referencedata.ourairports;

public record AirportCsvRecord(
    String icao,
    String iata,
    String name,
    double latitude,
    double longitude,
    Integer elevationFt,
    String isoCountry ) {
}
