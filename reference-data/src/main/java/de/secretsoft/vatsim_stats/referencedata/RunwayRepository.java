package de.secretsoft.vatsim_stats.referencedata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface RunwayRepository extends JpaRepository<Runway, Long> {

    List<Runway> findByAirportIcao( String airportIcao );

    /**
     * Bulk-removes all runway rows belonging to the given airports. Used by the daily OurAirports
     * import to replace an airport's runways instead of appending the whole dataset again on every
     * run. Self-transactional, because the import service has no surrounding transaction.
     */
    @Modifying
    @Transactional
    @Query( "delete from Runway r where r.airportIcao in :airportIcaos" )
    void deleteByAirportIcaoIn( @Param( "airportIcaos" ) Collection<String> airportIcaos );
}
