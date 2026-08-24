package de.secretsoft.vatsim_stats.ingestion.session;

import de.secretsoft.vatsim_stats.detection.AirportRef;
import de.secretsoft.vatsim_stats.referencedata.Airport;
import de.secretsoft.vatsim_stats.referencedata.AirportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AirportRepositoryLookupTest {

    private AirportRepository airportRepository;
    private AirportRepositoryLookup lookup;

    @BeforeEach
    void setUp() {
        airportRepository = mock( AirportRepository.class );
        lookup = new AirportRepositoryLookup( airportRepository );
    }

    @Test
    void returnsTheClosestCandidateWithinRadius() {
        Airport frankfurt = Airport.builder()
            .icao( "EDDF" ).name( "Frankfurt" ).latitude( 50.0264 ).longitude( 8.5431 ).elevationFt( 364 ).build();
        Airport egelsbach = Airport.builder()
            .icao( "EDFE" ).name( "Egelsbach" ).latitude( 49.9601 ).longitude( 8.6461 ).elevationFt( 384 ).build();
        when( airportRepository.findByLatitudeBetweenAndLongitudeBetween( anyDouble(), anyDouble(), anyDouble(), anyDouble() ) )
            .thenReturn( List.of( frankfurt, egelsbach ) );

        Optional<AirportRef> nearest = lookup.findNearest( 50.03, 8.55, 10.0 );

        assertThat( nearest ).isPresent();
        assertThat( nearest.get().icao() ).isEqualTo( "EDDF" );
        assertThat( nearest.get().elevationFt() ).isEqualTo( 364.0 );
    }

    @Test
    void returnsEmptyWhenNoCandidateIsWithinRadius() {
        Airport farAway = Airport.builder()
            .icao( "KJFK" ).name( "JFK" ).latitude( 40.6413 ).longitude( -73.7781 ).elevationFt( 13 ).build();
        when( airportRepository.findByLatitudeBetweenAndLongitudeBetween( anyDouble(), anyDouble(), anyDouble(), anyDouble() ) )
            .thenReturn( List.of( farAway ) );

        Optional<AirportRef> nearest = lookup.findNearest( 50.03, 8.55, 5.0 );

        assertThat( nearest ).isEmpty();
    }
}
