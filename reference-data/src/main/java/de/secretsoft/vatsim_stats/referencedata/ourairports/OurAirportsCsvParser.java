package de.secretsoft.vatsim_stats.referencedata.ourairports;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class OurAirportsCsvParser {

    private static final Set<String> RELEVANT_AIRPORT_TYPES =
        Set.of( "large_airport", "medium_airport", "small_airport" );

    public List<AirportCsvRecord> parseAirports( Reader csv ) {
        List<AirportCsvRecord> result = new ArrayList<>();
        try( CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord( true ).build().parse( csv ) ) {
            for( CSVRecord record : parser ) {
                String icao = firstNonBlank( record.get( "ident" ), record.get( "gps_code" ) );
                String type = record.get( "type" );
                if( icao == null || icao.isBlank() || !RELEVANT_AIRPORT_TYPES.contains( type ) ) {
                    continue;
                }
                result.add( new AirportCsvRecord(
                    icao,
                    blankToNull( record.get( "iata_code" ) ),
                    record.get( "name" ),
                    Double.parseDouble( record.get( "latitude_deg" ) ),
                    Double.parseDouble( record.get( "longitude_deg" ) ),
                    parseIntOrNull( record.get( "elevation_ft" ) ),
                    blankToNull( record.get( "iso_country" ) )
                ) );
            }
        } catch( IOException e ) {
            throw new UncheckedIOException( e );
        }
        return result;
    }

    public List<RunwayCsvRecord> parseRunways( Reader csv ) {
        List<RunwayCsvRecord> result = new ArrayList<>();
        try( CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord( true ).build().parse( csv ) ) {
            for( CSVRecord record : parser ) {
                String icao = record.get( "airport_ident" );
                boolean closed = "1".equals( record.get( "closed" ) );
                if( icao == null || icao.isBlank() || closed ) {
                    continue;
                }
                result.add( new RunwayCsvRecord(
                    icao,
                    blankToNull( record.get( "le_ident" ) ),
                    blankToNull( record.get( "he_ident" ) ),
                    parseDoubleOrNull( record.get( "le_latitude_deg" ) ),
                    parseDoubleOrNull( record.get( "le_longitude_deg" ) ),
                    parseDoubleOrNull( record.get( "he_latitude_deg" ) ),
                    parseDoubleOrNull( record.get( "he_longitude_deg" ) ),
                    parseIntOrNull( record.get( "length_ft" ) ),
                    blankToNull( record.get( "surface" ) )
                ) );
            }
        } catch( IOException e ) {
            throw new UncheckedIOException( e );
        }
        return result;
    }

    private static String firstNonBlank( String a, String b ) {
        if( a != null && !a.isBlank() ) {
            return a;
        }
        return ( b != null && !b.isBlank() ) ? b : null;
    }

    private static String blankToNull( String value ) {
        return ( value == null || value.isBlank() ) ? null : value;
    }

    private static Integer parseIntOrNull( String value ) {
        if( value == null || value.isBlank() ) {
            return null;
        }
        return (int) Double.parseDouble( value );
    }

    private static Double parseDoubleOrNull( String value ) {
        return ( value == null || value.isBlank() ) ? null : Double.parseDouble( value );
    }
}
