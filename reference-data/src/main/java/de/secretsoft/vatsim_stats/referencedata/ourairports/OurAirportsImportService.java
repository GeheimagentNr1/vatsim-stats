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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OurAirportsImportService {

    private static final String AIRPORTS_CSV_URL = "https://davidmegginson.github.io/ourairports-data/airports.csv";
    private static final String RUNWAYS_CSV_URL = "https://davidmegginson.github.io/ourairports-data/runways.csv";
    private static final int DELETE_BATCH_SIZE = 500;

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

        // Only runways whose airport actually survived the airport-type filter may be imported:
        // runways.csv also contains heliports, seaplane bases and similar, whose ICAO code will
        // never exist in the airport table and would violate runway.airport_icao's foreign key.
        Set<String> importedIcaos = airports.stream()
            .map( AirportCsvRecord::icao )
            .collect( Collectors.toSet() );
        List<RunwayCsvRecord> runways = parser.parseRunways( runwaysCsv ).stream()
            .filter( record -> importedIcaos.contains( record.airportIcao() ) )
            .toList();

        // Runways have no natural key beyond their surrogate id, so re-running the import would
        // otherwise append the whole dataset again. Replace per airport: drop the existing rows of
        // every airport we are about to (re-)import, then insert the freshly parsed set.
        deleteExistingRunways( runways.stream()
            .map( RunwayCsvRecord::airportIcao )
            .collect( Collectors.toCollection( LinkedHashSet::new ) ) );

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

    /** Deletes in chunks so the generated {@code IN (..)} clause stays within database limits. */
    private void deleteExistingRunways( Collection<String> airportIcaos ) {
        List<String> all = List.copyOf( airportIcaos );
        for( int start = 0; start < all.size(); start += DELETE_BATCH_SIZE ) {
            runwayRepository.deleteByAirportIcaoIn( all.subList( start, Math.min( start + DELETE_BATCH_SIZE, all.size() ) ) );
        }
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
