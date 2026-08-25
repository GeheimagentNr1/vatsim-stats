package de.secretsoft.vatsim_stats.referencedata;

import de.secretsoft.vatsim_stats.VatsimStatsApplication;
import de.secretsoft.vatsim_stats.referencedata.ourairports.OurAirportsImportResult;
import de.secretsoft.vatsim_stats.referencedata.ourairports.OurAirportsImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the daily OurAirports import against a real database: the airport foreign key on
 * {@code runway} must hold (runways of filtered-out airport types are skipped) and a repeated run
 * must not append duplicate runway rows.
 */
@Testcontainers
@SpringBootTest( classes = VatsimStatsApplication.class, properties = "vatsim.scheduling.enabled=false" )
class OurAirportsImportIT {

    private static final String AIRPORTS_CSV = """
        id,ident,type,name,latitude_deg,longitude_deg,elevation_ft,continent,iso_country,iso_region,municipality,scheduled_service,gps_code,iata_code,local_code,home_link,wikipedia_link,keywords
        3622,EDDF,large_airport,"Frankfurt am Main Airport",50.026421,8.543125,364,EU,DE,DE-HE,Frankfurt am Main,yes,EDDF,FRA,,,,
        9999,XXHP,heliport,"Some Heliport",53.0,13.0,20,EU,DE,DE-MV,Somewhere,no,XXHP,,,,,
        """;

    private static final String RUNWAYS_CSV = """
        id,airport_ref,airport_ident,length_ft,width_ft,surface,lighted,closed,le_ident,le_latitude_deg,le_longitude_deg,le_elevation_ft,le_heading_degT,le_displaced_threshold_ft,he_ident,he_latitude_deg,he_longitude_deg,he_elevation_ft,he_heading_degT,he_displaced_threshold_ft
        70172,3622,EDDF,13123,197,"Asphalt/Concrete",1,0,07C,50.03,8.52,364,70,,25C,50.03,8.58,364,250,
        70173,3622,EDDF,13123,197,"Asphalt/Concrete",1,0,07L,50.04,8.52,364,70,,25R,50.04,8.58,364,250,
        70174,9999,XXHP,300,30,Concrete,1,0,H1,53.0,13.0,20,0,,H1,53.0,13.0,20,180,
        """;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse( "timescale/timescaledb:latest-pg16" ).asCompatibleSubstituteFor( "postgres" ) );

    @DynamicPropertySource
    static void datasourceProperties( DynamicPropertyRegistry registry ) {
        registry.add( "spring.datasource.url", postgres::getJdbcUrl );
        registry.add( "spring.datasource.username", postgres::getUsername );
        registry.add( "spring.datasource.password", postgres::getPassword );
        registry.add( "spring.datasource.driver-class-name", () -> "org.postgresql.Driver" );
    }

    @Autowired
    private OurAirportsImportService importService;

    @Autowired
    private RunwayRepository runwayRepository;

    @Autowired
    private AirportRepository airportRepository;

    @Test
    void repeatedImportsKeepExactlyOneRunwaySetAndNeverViolateTheAirportForeignKey() {
        OurAirportsImportResult first = importService.importFrom(
            new StringReader( AIRPORTS_CSV ), new StringReader( RUNWAYS_CSV ) );

        assertThat( first.airportsUpserted() ).isEqualTo( 1 );
        assertThat( first.runwaysUpserted() ).isEqualTo( 2 );
        assertThat( airportRepository.findById( "XXHP" ) ).isEmpty();

        List<Runway> afterFirst = runwayRepository.findByAirportIcao( "EDDF" );
        assertThat( afterFirst ).hasSize( 2 );

        importService.importFrom( new StringReader( AIRPORTS_CSV ), new StringReader( RUNWAYS_CSV ) );
        importService.importFrom( new StringReader( AIRPORTS_CSV ), new StringReader( RUNWAYS_CSV ) );

        assertThat( runwayRepository.findByAirportIcao( "EDDF" ) ).hasSize( 2 );
        assertThat( runwayRepository.count() ).isEqualTo( 2 );
    }
}
