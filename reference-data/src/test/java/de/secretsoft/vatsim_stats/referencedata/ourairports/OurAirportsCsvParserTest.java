package de.secretsoft.vatsim_stats.referencedata.ourairports;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OurAirportsCsvParserTest {

    private static final String AIRPORTS_CSV = """
        id,ident,type,name,latitude_deg,longitude_deg,elevation_ft,continent,iso_country,iso_region,municipality,scheduled_service,gps_code,iata_code,local_code,home_link,wikipedia_link,keywords
        3622,EDDF,large_airport,"Frankfurt am Main Airport",50.026421,8.543125,364,EU,DE,DE-HE,Frankfurt am Main,yes,EDDF,FRA,,,,
        3623,EDXX,heliport,"Some Heliport",51.0,9.0,10,EU,DE,DE-HE,Nowhere,no,EDXX,,,,,
        3624,,small_airport,"No Ident Airport",52.0,10.0,5,EU,DE,DE-HE,Nowhere,no,,,,,,
        """;

    private static final String RUNWAYS_CSV = """
        id,airport_ref,airport_ident,length_ft,width_ft,surface,lighted,closed,le_ident,le_latitude_deg,le_longitude_deg,le_elevation_ft,le_heading_degT,le_displaced_threshold_ft,he_ident,he_latitude_deg,he_longitude_deg,he_elevation_ft,he_heading_degT,he_displaced_threshold_ft
        70172,3622,EDDF,13123,197,"Asphalt/Concrete",1,0,07C,50.03,8.52,364,70,,25C,50.03,8.58,364,250,
        70173,3622,EDDF,3000,100,"Asphalt",1,1,18,50.01,8.50,364,180,,36,50.02,8.51,364,0,
        """;

    private final OurAirportsCsvParser parser = new OurAirportsCsvParser();

    @Test
    void parsesOnlyRealAirportsWithAnIdent() {
        List<AirportCsvRecord> airports = parser.parseAirports( new StringReader( AIRPORTS_CSV ) );

        assertThat( airports ).hasSize( 1 );
        AirportCsvRecord frankfurt = airports.get( 0 );
        assertThat( frankfurt.icao() ).isEqualTo( "EDDF" );
        assertThat( frankfurt.iata() ).isEqualTo( "FRA" );
        assertThat( frankfurt.name() ).isEqualTo( "Frankfurt am Main Airport" );
        assertThat( frankfurt.latitude() ).isEqualTo( 50.026421 );
        assertThat( frankfurt.longitude() ).isEqualTo( 8.543125 );
        assertThat( frankfurt.elevationFt() ).isEqualTo( 364 );
        assertThat( frankfurt.isoCountry() ).isEqualTo( "DE" );
    }

    @Test
    void parsesOnlyNonClosedRunways() {
        List<RunwayCsvRecord> runways = parser.parseRunways( new StringReader( RUNWAYS_CSV ) );

        assertThat( runways ).hasSize( 1 );
        RunwayCsvRecord runway = runways.get( 0 );
        assertThat( runway.airportIcao() ).isEqualTo( "EDDF" );
        assertThat( runway.leIdent() ).isEqualTo( "07C" );
        assertThat( runway.heIdent() ).isEqualTo( "25C" );
        assertThat( runway.lengthFt() ).isEqualTo( 13123 );
    }
}
