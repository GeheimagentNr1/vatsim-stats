package de.secretsoft.vatsim_stats.referencedata.ourairports;

import de.secretsoft.vatsim_stats.referencedata.Airport;
import de.secretsoft.vatsim_stats.referencedata.AirportRepository;
import de.secretsoft.vatsim_stats.referencedata.Runway;
import de.secretsoft.vatsim_stats.referencedata.RunwayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OurAirportsImportServiceTest {

    private static final String AIRPORTS_CSV_HEADER =
        "id,ident,type,name,latitude_deg,longitude_deg,elevation_ft,continent,iso_country,iso_region,"
            + "municipality,scheduled_service,gps_code,iata_code,local_code,home_link,wikipedia_link,keywords\n";

    private static final String RUNWAYS_CSV_HEADER =
        "id,airport_ref,airport_ident,length_ft,width_ft,surface,lighted,closed,le_ident,le_latitude_deg,"
            + "le_longitude_deg,le_elevation_ft,le_heading_degT,le_displaced_threshold_ft,he_ident,"
            + "he_latitude_deg,he_longitude_deg,he_elevation_ft,he_heading_degT,he_displaced_threshold_ft\n";

    private static final String AIRPORTS_CSV = AIRPORTS_CSV_HEADER
        + "3622,EDDF,large_airport,\"Frankfurt am Main Airport\",50.026421,8.543125,364,EU,DE,DE-HE,"
        + "Frankfurt am Main,yes,EDDF,FRA,,,,\n"
        // A heliport: filtered out by the airport parser, so its runway must be skipped as well.
        + "9999,EDBH,heliport,\"Some Heliport\",53.0,13.0,20,EU,DE,DE-MV,Somewhere,no,EDBH,,,,,\n";

    private static final String RUNWAYS_CSV = RUNWAYS_CSV_HEADER
        + "70172,3622,EDDF,13123,197,\"Asphalt/Concrete\",1,0,07C,50.03,8.52,364,70,,25C,50.03,8.58,364,250,\n"
        + "70173,9999,EDBH,300,30,Concrete,1,0,H1,53.0,13.0,20,0,,H1,53.0,13.0,20,180,\n";

    private AirportRepository airportRepository;
    private RunwayRepository runwayRepository;
    private List<Runway> storedRunways;
    private OurAirportsImportService service;

    @BeforeEach
    void setUp() {
        airportRepository = mock( AirportRepository.class );
        runwayRepository = mock( RunwayRepository.class );
        storedRunways = new ArrayList<>();
        when( airportRepository.findById( any() ) ).thenReturn( Optional.empty() );
        when( runwayRepository.findByAirportIcao( any() ) ).thenReturn( List.of() );
        when( runwayRepository.save( any() ) ).thenAnswer( invocation -> {
            Runway runway = invocation.getArgument( 0 );
            storedRunways.add( runway );
            return runway;
        } );
        doAnswer( invocation -> {
            Collection<String> icaos = invocation.getArgument( 0 );
            storedRunways.removeIf( runway -> icaos.contains( runway.getAirportIcao() ) );
            return null;
        } ).when( runwayRepository ).deleteByAirportIcaoIn( any() );
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

    @Test
    void skipsRunwaysWhoseAirportWasFilteredOutByTheAirportTypeFilter() {
        service.importFrom( new StringReader( AIRPORTS_CSV ), new StringReader( RUNWAYS_CSV ) );

        assertThat( storedRunways ).extracting( Runway::getAirportIcao ).containsExactly( "EDDF" );
        verify( runwayRepository, never() ).deleteByAirportIcaoIn( argThat( icaos -> icaos.contains( "EDBH" ) ) );
    }

    @Test
    void reimportingTheSameDataDoesNotDuplicateRunways() {
        service.importFrom( new StringReader( AIRPORTS_CSV ), new StringReader( RUNWAYS_CSV ) );
        assertThat( storedRunways ).hasSize( 1 );

        service.importFrom( new StringReader( AIRPORTS_CSV ), new StringReader( RUNWAYS_CSV ) );

        assertThat( storedRunways ).hasSize( 1 );
        assertThat( storedRunways ).extracting( Runway::getAirportIcao ).containsExactly( "EDDF" );
        verify( runwayRepository, times( 2 ) ).deleteByAirportIcaoIn( argThat( icaos -> icaos.contains( "EDDF" ) ) );
    }
}
