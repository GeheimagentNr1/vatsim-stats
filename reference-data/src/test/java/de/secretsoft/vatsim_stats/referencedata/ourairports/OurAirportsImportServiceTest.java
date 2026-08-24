package de.secretsoft.vatsim_stats.referencedata.ourairports;

import de.secretsoft.vatsim_stats.referencedata.Airport;
import de.secretsoft.vatsim_stats.referencedata.AirportRepository;
import de.secretsoft.vatsim_stats.referencedata.Runway;
import de.secretsoft.vatsim_stats.referencedata.RunwayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.StringReader;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OurAirportsImportServiceTest {

    private static final String AIRPORTS_CSV = """
        id,ident,type,name,latitude_deg,longitude_deg,elevation_ft,continent,iso_country,iso_region,municipality,scheduled_service,gps_code,iata_code,local_code,home_link,wikipedia_link,keywords
        3622,EDDF,large_airport,"Frankfurt am Main Airport",50.026421,8.543125,364,EU,DE,DE-HE,Frankfurt am Main,yes,EDDF,FRA,,,,
        """;

    private static final String RUNWAYS_CSV = """
        id,airport_ref,airport_ident,length_ft,width_ft,surface,lighted,closed,le_ident,le_latitude_deg,le_longitude_deg,le_elevation_ft,le_heading_degT,le_displaced_threshold_ft,he_ident,he_latitude_deg,he_longitude_deg,he_elevation_ft,he_heading_degT,he_displaced_threshold_ft
        70172,3622,EDDF,13123,197,"Asphalt/Concrete",1,0,07C,50.03,8.52,364,70,,25C,50.03,8.58,364,250,
        """;

    private AirportRepository airportRepository;
    private RunwayRepository runwayRepository;
    private OurAirportsImportService service;

    @BeforeEach
    void setUp() {
        airportRepository = mock( AirportRepository.class );
        runwayRepository = mock( RunwayRepository.class );
        when( airportRepository.findById( any() ) ).thenReturn( Optional.empty() );
        when( runwayRepository.findByAirportIcao( any() ) ).thenReturn( List.of() );
        service = new OurAirportsImportService( new OurAirportsCsvParser(), airportRepository, runwayRepository );
    }

    @Test
    void upsertsParsedAirportsAndRunways() {
        OurAirportsImportResult result = service.importFrom(
            new StringReader( AIRPORTS_CSV ), new StringReader( RUNWAYS_CSV ) );

        assertThat( result.airportsUpserted() ).isEqualTo( 1 );
        assertThat( result.runwaysUpserted() ).isEqualTo( 1 );

        ArgumentCaptor<Airport> airportCaptor = ArgumentCaptor.forClass( Airport.class );
        verify( airportRepository ).save( airportCaptor.capture() );
        assertThat( airportCaptor.getValue().getIcao() ).isEqualTo( "EDDF" );

        ArgumentCaptor<Runway> runwayCaptor = ArgumentCaptor.forClass( Runway.class );
        verify( runwayRepository ).save( runwayCaptor.capture() );
        assertThat( runwayCaptor.getValue().getAirportIcao() ).isEqualTo( "EDDF" );
    }
}
