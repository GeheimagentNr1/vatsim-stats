package de.secretsoft.vatsim_stats.ingestion;

import de.secretsoft.vatsim_stats.VatsimStatsApplication;
import de.secretsoft.vatsim_stats.detection.AirportEventType;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotAirportEvent;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotAirportEventRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotSession;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotSessionRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.SessionStatus;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimDataFeed;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimDataFeedClient;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimFlightPlan;
import de.secretsoft.vatsim_stats.ingestion.vatsimapi.VatsimPilot;
import de.secretsoft.vatsim_stats.referencedata.Airport;
import de.secretsoft.vatsim_stats.referencedata.AirportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(
    classes = { VatsimStatsApplication.class, IngestionEndToEndIT.TestClockConfig.class },
    properties = {
        "spring.main.allow-bean-definition-overriding=true",
        // No background scheduler: this test drives poller.pollOnce() by hand.
        "vatsim.scheduling.enabled=false"
    }
)
class IngestionEndToEndIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse( "timescale/timescaledb:latest-pg18" ).asCompatibleSubstituteFor( "postgres" ) );

    @DynamicPropertySource
    static void datasourceProperties( DynamicPropertyRegistry registry ) {
        registry.add( "spring.datasource.url", postgres::getJdbcUrl );
        registry.add( "spring.datasource.username", postgres::getUsername );
        registry.add( "spring.datasource.password", postgres::getPassword );
    }

    @TestConfiguration
    static class TestClockConfig {
        static final MutableClock CLOCK = new MutableClock( Instant.parse( "2026-08-24T10:00:00Z" ) );

        @Bean
        @Primary
        Clock clock() {
            return CLOCK;
        }
    }

    static class MutableClock extends Clock {
        private Instant instant;

        MutableClock( Instant initial ) {
            this.instant = initial;
        }

        void advance( Duration duration ) {
            instant = instant.plus( duration );
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone( ZoneId zone ) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @MockitoBean
    private VatsimDataFeedClient feedClient;

    @Autowired
    private IngestionPoller poller;

    @Autowired
    private AirportRepository airportRepository;

    @Autowired
    private PilotSessionRepository pilotSessionRepository;

    @Autowired
    private PilotAirportEventRepository pilotAirportEventRepository;

    @BeforeEach
    void setUp() {
        TestClockConfig.CLOCK.instant = Instant.parse( "2026-08-24T10:00:00Z" );
        airportRepository.save( Airport.builder()
            .icao( "EDDF" ).name( "Frankfurt" ).latitude( 50.0264 ).longitude( 8.5431 ).elevationFt( 364 ).build() );
    }

    private VatsimPilot pilot( double altitudeFt, double groundspeedKt ) {
        return new VatsimPilot(
            123456L, "DLH400", 50.0264, 8.5431, (int) altitudeFt, (int) groundspeedKt, 270, "2000", 1013,
            Instant.parse( "2026-08-24T09:45:00Z" ),
            new VatsimFlightPlan( "EDDF", "EDDM", "A320" ) );
    }

    @Test
    void aFullFlightIsRecordedAsACompletedSessionWithTakeoffAndLandingEvents() {
        when( feedClient.fetchCurrent() )
            .thenReturn( new VatsimDataFeed( List.of( pilot( 3000, 250 ) ), List.of() ) )
            .thenReturn( new VatsimDataFeed( List.of( pilot( 3000, 250 ) ), List.of() ) );
        poller.pollOnce();
        TestClockConfig.CLOCK.advance( Duration.ofSeconds( 15 ) );
        poller.pollOnce();

        when( feedClient.fetchCurrent() ).thenReturn( new VatsimDataFeed( List.of( pilot( 550, 15 ) ), List.of() ) );
        for( int i = 0; i < 7; i++ ) {
            TestClockConfig.CLOCK.advance( Duration.ofSeconds( 15 ) );
            poller.pollOnce();
        }

        PilotSession session = pilotSessionRepository
            .findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc(
                123456L, "DLH400", Instant.parse( "2026-08-24T09:45:00Z" ) )
            .orElseThrow();
        assertThat( session.getStatus() ).isEqualTo( SessionStatus.COMPLETED );

        List<PilotAirportEvent> events = pilotAirportEventRepository.findByPilotSessionOrderByOccurredAt( session );
        assertThat( events ).hasSize( 1 );
        assertThat( events.get( 0 ).getEventType() ).isEqualTo( AirportEventType.LANDING );
        assertThat( events.get( 0 ).getAirportIcao() ).isEqualTo( "EDDF" );
    }
}
