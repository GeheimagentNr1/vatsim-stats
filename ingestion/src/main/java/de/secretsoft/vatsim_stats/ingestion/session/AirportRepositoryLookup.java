package de.secretsoft.vatsim_stats.ingestion.session;

import de.secretsoft.vatsim_stats.detection.AirportRef;
import de.secretsoft.vatsim_stats.detection.Haversine;
import de.secretsoft.vatsim_stats.detection.NearestAirportLookup;
import de.secretsoft.vatsim_stats.referencedata.Airport;
import de.secretsoft.vatsim_stats.referencedata.AirportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AirportRepositoryLookup implements NearestAirportLookup {

    private final AirportRepository airportRepository;

    @Override
    public Optional<AirportRef> findNearest( double latitude, double longitude, double radiusNm ) {
        double latDeltaDeg = radiusNm / 60.0;
        double lonDeltaDeg = latDeltaDeg / Math.max( 0.1, Math.cos( Math.toRadians( latitude ) ) );

        List<Airport> candidates = airportRepository.findByLatitudeBetweenAndLongitudeBetween(
            latitude - latDeltaDeg, latitude + latDeltaDeg,
            longitude - lonDeltaDeg, longitude + lonDeltaDeg );

        Airport nearest = null;
        double nearestDistanceNm = Double.MAX_VALUE;
        for( Airport candidate : candidates ) {
            double distanceNm = Haversine.distanceNm( latitude, longitude, candidate.getLatitude(), candidate.getLongitude() );
            if( distanceNm <= radiusNm && distanceNm < nearestDistanceNm ) {
                nearest = candidate;
                nearestDistanceNm = distanceNm;
            }
        }

        if( nearest == null ) {
            return Optional.empty();
        }
        double elevationFt = nearest.getElevationFt() != null ? nearest.getElevationFt() : 0;
        return Optional.of( new AirportRef( nearest.getIcao(), nearest.getLatitude(), nearest.getLongitude(), elevationFt ) );
    }
}
