package de.secretsoft.vatsim_stats.referencedata.ourairports;

public record RunwayCsvRecord(
    String airportIcao,
    String leIdent,
    String heIdent,
    Double leLatitude,
    Double leLongitude,
    Double heLatitude,
    Double heLongitude,
    Integer lengthFt,
    String surface ) {
}
