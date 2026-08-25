package de.secretsoft.vatsim_stats.ingestion.domain;

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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest( classes = VatsimStatsApplication.class, properties = "vatsim.scheduling.enabled=false" )
class PilotSessionRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse( "timescale/timescaledb:latest-pg16" ).asCompatibleSubstituteFor( "postgres" ) );

    @DynamicPropertySource
    static void datasourceProperties( DynamicPropertyRegistry registry ) {
        registry.add( "spring.datasource.url", postgres::getJdbcUrl );
        registry.add( "spring.datasource.username", postgres::getUsername );
        registry.add( "spring.datasource.password", postgres::getPassword );
    }

    @Autowired
    private PilotSessionRepository pilotSessionRepository;

    @Autowired
    private PilotTrackPointRepository pilotTrackPointRepository;

    @Test
    void savesSessionAndFindsItByNaturalKey() {
        Instant logonTime = Instant.parse( "2026-08-24T10:00:00Z" );
        PilotSession session = PilotSession.builder()
            .cid( 123456L )
            .callsign( "DLH400" )
            .logonTime( logonTime )
            .sequenceNumber( 0 )
            .status( SessionStatus.ACTIVE )
            .startedAt( logonTime )
            .build();

        pilotSessionRepository.save( session );

        Optional<PilotSession> found = pilotSessionRepository
            .findByCidAndCallsignAndLogonTimeAndSequenceNumber( 123456L, "DLH400", logonTime, 0 );
        assertThat( found ).isPresent();
        assertThat( found.get().getStatus() ).isEqualTo( SessionStatus.ACTIVE );
    }

    @Test
    void findsMostRecentTrackPointsForRestartReconstruction() {
        Instant logonTime = Instant.parse( "2026-08-24T10:00:00Z" );
        for( int i = 0; i < 3; i++ ) {
            pilotTrackPointRepository.save( PilotTrackPoint.builder()
                .recordedAt( logonTime.plusSeconds( i * 15L ) )
                .cid( 123456L )
                .callsign( "DLH400" )
                .logonTime( logonTime )
                .latitude( 50.0 )
                .longitude( 8.5 )
                .altitudeFt( 3000 )
                .groundspeedKt( 250 )
                .build() );
        }

        List<PilotTrackPoint> recent = pilotTrackPointRepository
            .findTop10ByCidAndCallsignAndLogonTimeOrderByRecordedAtDesc( 123456L, "DLH400", logonTime );

        assertThat( recent ).hasSize( 3 );
        assertThat( recent.get( 0 ).getRecordedAt() ).isEqualTo( logonTime.plusSeconds( 30 ) );
    }
}
