package de.secretsoft.vatsim_stats.referencedata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AirportRepository extends JpaRepository<Airport, String> {

    List<Airport> findByLatitudeBetweenAndLongitudeBetween(
        double minLatitude, double maxLatitude, double minLongitude, double maxLongitude );
}
