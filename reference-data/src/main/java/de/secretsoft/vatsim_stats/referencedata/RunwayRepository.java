package de.secretsoft.vatsim_stats.referencedata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RunwayRepository extends JpaRepository<Runway, Long> {

    List<Runway> findByAirportIcao( String airportIcao );
}
