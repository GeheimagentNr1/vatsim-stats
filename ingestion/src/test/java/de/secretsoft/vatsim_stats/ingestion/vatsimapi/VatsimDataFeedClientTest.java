package de.secretsoft.vatsim_stats.ingestion.vatsimapi;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class VatsimDataFeedClientTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void deserializesPilotsAndControllersFromRealShapedFeed() throws Exception {
        try( InputStream in = getClass().getResourceAsStream( "/vatsim-data-sample.json" ) ) {
            VatsimDataFeed feed = objectMapper.readValue( in, VatsimDataFeed.class );

            assertThat( feed.pilots() ).hasSize( 2 );
            VatsimPilot dlh400 = feed.pilots().get( 0 );
            assertThat( dlh400.cid() ).isEqualTo( 123456L );
            assertThat( dlh400.callsign() ).isEqualTo( "DLH400" );
            assertThat( dlh400.logonTime() ).isEqualTo( Instant.parse( "2026-08-24T09:45:00Z" ) );
            assertThat( dlh400.flightPlan() ).isNotNull();
            assertThat( dlh400.flightPlan().aircraftShort() ).isEqualTo( "A320" );
            assertThat( dlh400.flightPlan().departure() ).isEqualTo( "EDDF" );

            VatsimPilot vfrPilot = feed.pilots().get( 1 );
            assertThat( vfrPilot.flightPlan() ).isNull();

            assertThat( feed.controllers() ).hasSize( 1 );
            assertThat( feed.controllers().get( 0 ).callsign() ).isEqualTo( "EDDF_TWR" );
            assertThat( feed.controllers().get( 0 ).facility() ).isEqualTo( 4 );
        }
    }
}
