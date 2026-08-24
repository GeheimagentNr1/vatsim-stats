package de.secretsoft.vatsim_stats.detection;

import java.util.Optional;

public interface NearestAirportLookup {

    Optional<AirportRef> findNearest( double latitude, double longitude, double radiusNm );
}
