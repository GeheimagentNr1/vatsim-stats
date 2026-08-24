package de.secretsoft.vatsim_stats.referencedata.ourairports;

import de.secretsoft.vatsim_stats.referencedata.Airport;
import de.secretsoft.vatsim_stats.referencedata.AirportRepository;
import de.secretsoft.vatsim_stats.referencedata.Runway;
import de.secretsoft.vatsim_stats.referencedata.RunwayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OurAirportsImportService {

    private static final String AIRPORTS_CSV_URL = "https://davidmegginson.github.io/ourairports-data/airports.csv";
    private static final String RUNWAYS_CSV_URL = "https://davidmegginson.github.io/ourairports-data/runways.csv";

    private final OurAirportsCsvParser parser;
    private final AirportRepository airportRepository;
    private final RunwayRepository runwayRepository;

    public OurAirportsImportResult importFromOurAirports() {
        RestClient client = RestClient.builder()
            .requestFactory( timeoutingRequestFactory() )
            .build();

        try( Reader airportsCsv = new InputStreamReader( download( client, AIRPORTS_CSV_URL ), StandardCharsets.UTF_8 );
             Reader runwaysCsv = new InputStreamReader( download( client, RUNWAYS_CSV_URL ), StandardCharsets.UTF_8 ) ) {
            return importFrom( airportsCsv, runwaysCsv );
        } catch( java.io.IOException e ) {
            throw new java.io.UncheckedIOException( e );
        }
    }

    public OurAirportsImportResult importFrom( Reader airportsCsv, Reader runwaysCsv ) {
        List<AirportCsvRecord> airports = parser.parseAirports( airportsCsv );
        for( AirportCsvRecord record : airports ) {
            Airport airport = airportRepository.findById( record.icao() )
                .orElseGet( () -> Airport.builder().icao( record.icao() ).build() );
            airport.setIata( record.iata() );
            airport.setName( record.name() );
            airport.setLatitude( record.latitude() );
            airport.setLongitude( record.longitude() );
            airport.setElevationFt( record.elevationFt() );
            airport.setIsoCountry( record.isoCountry() );
            airportRepository.save( airport );
        }

        List<RunwayCsvRecord> runways = parser.parseRunways( runwaysCsv );
        for( RunwayCsvRecord record : runways ) {
            Runway runway = Runway.builder()
                .airportIcao( record.airportIcao() )
                .leIdent( record.leIdent() )
                .heIdent( record.heIdent() )
                .leLatitude( record.leLatitude() )
                .leLongitude( record.leLongitude() )
                .heLatitude( record.heLatitude() )
                .heLongitude( record.heLongitude() )
                .lengthFt( record.lengthFt() )
                .surface( record.surface() )
                .build();
            runwayRepository.save( runway );
        }

        return new OurAirportsImportResult( airports.size(), runways.size() );
    }

    private java.io.InputStream download( RestClient client, String url ) {
        byte[] body = client.get().uri( url ).retrieve().body( byte[].class );
        return new java.io.ByteArrayInputStream( body != null ? body : new byte[0] );
    }

    private SimpleClientHttpRequestFactory timeoutingRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout( (int) Duration.ofSeconds( 30 ).toMillis() );
        factory.setReadTimeout( (int) Duration.ofSeconds( 60 ).toMillis() );
        return factory;
    }
}
