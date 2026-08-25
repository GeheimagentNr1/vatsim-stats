package de.secretsoft.vatsim_stats.referencedata;

import de.secretsoft.vatsim_stats.VatsimStatsApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest( classes = VatsimStatsApplication.class, properties = "vatsim.scheduling.enabled=false" )
class AirportRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse( "timescale/timescaledb:latest-pg18" ).asCompatibleSubstituteFor( "postgres" ) );

    @DynamicPropertySource
    static void datasourceProperties( DynamicPropertyRegistry registry ) {
        registry.add( "spring.datasource.url", postgres::getJdbcUrl );
        registry.add( "spring.datasource.username", postgres::getUsername );
        registry.add( "spring.datasource.password", postgres::getPassword );
        registry.add( "spring.datasource.driver-class-name", () -> "org.postgresql.Driver" );
    }

    @Autowired
    private AirportRepository airportRepository;

    @Test
    void savesAndReadsAnAirport() {
        Airport airport = Airport.builder()
            .icao( "EDDF" )
            .iata( "FRA" )
            .name( "Frankfurt am Main" )
            .latitude( 50.0264 )
            .longitude( 8.5431 )
            .elevationFt( 364 )
            .isoCountry( "DE" )
            .build();

        airportRepository.save( airport );

        Optional<Airport> found = airportRepository.findById( "EDDF" );
        assertThat( found ).isPresent();
        assertThat( found.get().getName() ).isEqualTo( "Frankfurt am Main" );
    }
}
