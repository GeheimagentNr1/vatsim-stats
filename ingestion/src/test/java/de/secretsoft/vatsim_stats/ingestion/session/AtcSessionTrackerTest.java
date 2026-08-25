package de.secretsoft.vatsim_stats.ingestion.session;

import de.secretsoft.vatsim_stats.ingestion.domain.AtcSession;
import de.secretsoft.vatsim_stats.ingestion.domain.AtcSessionRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.AtcSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class AtcSessionTrackerTest {

    private static final Instant LOGON = Instant.parse( "2026-08-24T09:00:00Z" );

    private FakeAtcSessionRepository repository;
    private AtcSessionTracker tracker;

    @BeforeEach
    void setUp() {
        repository = new FakeAtcSessionRepository();
        tracker = new AtcSessionTracker( repository );
    }

    private AtcSnapshot snapshot( int offsetSeconds ) {
        return AtcSnapshot.builder()
            .recordedAt( LOGON.plusSeconds( offsetSeconds ) )
            .cid( 111222L )
            .callsign( "EDDF_TWR" )
            .logonTime( LOGON )
            .frequency( "119.900" )
            .facility( 4 )
            .build();
    }

    @Test
    void opensASessionOnFirstAppearanceAndDoesNotDuplicateIt() {
        tracker.processSnapshots( List.of( snapshot( 0 ) ) );
        tracker.processSnapshots( List.of( snapshot( 15 ) ) );

        List<AtcSession> all = repository.all();
        assertThat( all ).hasSize( 1 );
        assertThat( all.get( 0 ).getEndedAt() ).isNull();
        assertThat( all.get( 0 ).getStartedAt() ).isEqualTo( LOGON );
    }

    @Test
    void closesTheSessionWithTheLastSeenTimestampWhenTheControllerDisappears() {
        tracker.processSnapshots( List.of( snapshot( 0 ) ) );
        tracker.processSnapshots( List.of( snapshot( 15 ) ) );
        tracker.processSnapshots( List.of() );

        AtcSession session = repository.all().get( 0 );
        assertThat( session.getEndedAt() ).isEqualTo( LOGON.plusSeconds( 15 ) );
    }

    @Test
    void reconstructsOpenSessionsOnStartup() {
        repository.save( AtcSession.builder()
            .cid( 111222L ).callsign( "EDDF_TWR" ).logonTime( LOGON )
            .facility( 4 ).startedAt( LOGON ).build() );

        AtcSessionTracker restarted = new AtcSessionTracker( repository );
        restarted.reconstructOpenSessions();
        restarted.processSnapshots( List.of() );

        AtcSession session = repository.all().get( 0 );
        assertThat( session.getEndedAt() ).isEqualTo( LOGON );
    }

    private static class FakeAtcSessionRepository implements AtcSessionRepository {

        private final Map<Long, AtcSession> byId = new HashMap<>();
        private final AtomicLong sequence = new AtomicLong();

        List<AtcSession> all() {
            return List.copyOf( byId.values() );
        }

        @Override
        public Optional<AtcSession> findByCidAndCallsignAndLogonTime( Long cid, String callsign, Instant logonTime ) {
            return byId.values().stream()
                .filter( s -> s.getCid().equals( cid ) && s.getCallsign().equals( callsign )
                    && s.getLogonTime().equals( logonTime ) )
                .findFirst();
        }

        @Override
        public List<AtcSession> findByEndedAtIsNull() {
            return byId.values().stream().filter( s -> s.getEndedAt() == null ).toList();
        }

        @Override
        public <S extends AtcSession> S save( S entity ) {
            if( entity.getId() == null ) {
                entity.setId( sequence.incrementAndGet() );
            }
            byId.put( entity.getId(), entity );
            return entity;
        }

        @Override
        public Optional<AtcSession> findById( Long id ) { return Optional.ofNullable( byId.get( id ) ); }
        @Override
        public <S extends AtcSession> List<S> saveAll( Iterable<S> entities ) { throw new UnsupportedOperationException(); }
        @Override
        public boolean existsById( Long id ) { throw new UnsupportedOperationException(); }
        @Override
        public List<AtcSession> findAll() { throw new UnsupportedOperationException(); }
        @Override
        public List<AtcSession> findAllById( Iterable<Long> ids ) { throw new UnsupportedOperationException(); }
        @Override
        public long count() { throw new UnsupportedOperationException(); }
        @Override
        public void deleteById( Long id ) { throw new UnsupportedOperationException(); }
        @Override
        public void delete( AtcSession entity ) { throw new UnsupportedOperationException(); }
        @Override
        public void deleteAllById( Iterable<? extends Long> ids ) { throw new UnsupportedOperationException(); }
        @Override
        public void deleteAll( Iterable<? extends AtcSession> entities ) { throw new UnsupportedOperationException(); }
        @Override
        public void deleteAll() { throw new UnsupportedOperationException(); }
        @Override
        public List<AtcSession> findAll( org.springframework.data.domain.Sort sort ) { throw new UnsupportedOperationException(); }
        @Override
        public org.springframework.data.domain.Page<AtcSession> findAll( org.springframework.data.domain.Pageable pageable ) { throw new UnsupportedOperationException(); }
        @Override
        public void flush() { throw new UnsupportedOperationException(); }
        @Override
        public <S extends AtcSession> S saveAndFlush( S entity ) { throw new UnsupportedOperationException(); }
        @Override
        public <S extends AtcSession> List<S> saveAllAndFlush( Iterable<S> entities ) { throw new UnsupportedOperationException(); }
        @Override
        public void deleteAllInBatch( Iterable<AtcSession> entities ) { throw new UnsupportedOperationException(); }
        @Override
        public void deleteAllByIdInBatch( Iterable<Long> ids ) { throw new UnsupportedOperationException(); }
        @Override
        public void deleteAllInBatch() { throw new UnsupportedOperationException(); }
        @Override
        public AtcSession getOne( Long id ) { throw new UnsupportedOperationException(); }
        @Override
        public AtcSession getById( Long id ) { throw new UnsupportedOperationException(); }
        @Override
        public AtcSession getReferenceById( Long id ) { throw new UnsupportedOperationException(); }
        @Override
        public <S extends AtcSession> Optional<S> findOne( org.springframework.data.domain.Example<S> example ) { throw new UnsupportedOperationException(); }
        @Override
        public <S extends AtcSession> List<S> findAll( org.springframework.data.domain.Example<S> example ) { throw new UnsupportedOperationException(); }
        @Override
        public <S extends AtcSession> List<S> findAll( org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort ) { throw new UnsupportedOperationException(); }
        @Override
        public <S extends AtcSession> org.springframework.data.domain.Page<S> findAll( org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable ) { throw new UnsupportedOperationException(); }
        @Override
        public <S extends AtcSession> long count( org.springframework.data.domain.Example<S> example ) { throw new UnsupportedOperationException(); }
        @Override
        public <S extends AtcSession> boolean exists( org.springframework.data.domain.Example<S> example ) { throw new UnsupportedOperationException(); }
        @Override
        public <S extends AtcSession, R> R findBy( org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction ) { throw new UnsupportedOperationException(); }
    }
}
