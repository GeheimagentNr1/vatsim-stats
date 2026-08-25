package de.secretsoft.vatsim_stats.ingestion.session;

import de.secretsoft.vatsim_stats.ingestion.domain.PilotSession;
import de.secretsoft.vatsim_stats.ingestion.domain.PilotSessionRepository;
import de.secretsoft.vatsim_stats.ingestion.domain.SessionStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

class InMemoryPilotSessionRepository implements PilotSessionRepository {

    private final Map<Long, PilotSession> byId = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public Optional<PilotSession> findByCidAndCallsignAndLogonTimeAndSequenceNumber(
        Long cid, String callsign, Instant logonTime, int sequenceNumber ) {
        return byId.values().stream()
            .filter( s -> s.getCid().equals( cid ) && s.getCallsign().equals( callsign )
                && s.getLogonTime().equals( logonTime ) && s.getSequenceNumber() == sequenceNumber )
            .findFirst();
    }

    @Override
    public Optional<PilotSession> findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc(
        Long cid, String callsign, Instant logonTime ) {
        return byId.values().stream()
            .filter( s -> s.getCid().equals( cid ) && s.getCallsign().equals( callsign )
                && s.getLogonTime().equals( logonTime ) )
            .max( Comparator.comparingInt( PilotSession::getSequenceNumber ) );
    }

    @Override
    public List<PilotSession> findByStatus( SessionStatus status ) {
        return byId.values().stream().filter( s -> s.getStatus() == status ).toList();
    }

    @Override
    public <S extends PilotSession> S save( S entity ) {
        if( entity.getId() == null ) {
            entity.setId( sequence.incrementAndGet() );
        }
        byId.put( entity.getId(), entity );
        return entity;
    }

    @Override
    public Optional<PilotSession> findById( Long id ) {
        return Optional.ofNullable( byId.get( id ) );
    }

    // Remaining JpaRepository methods are unused by the orchestrator and intentionally left
    // unimplemented for this in-memory test double.
    @Override
    public <S extends PilotSession> List<S> saveAll( Iterable<S> entities ) { throw new UnsupportedOperationException(); }
    @Override
    public boolean existsById( Long id ) { throw new UnsupportedOperationException(); }
    @Override
    public List<PilotSession> findAll() { throw new UnsupportedOperationException(); }
    @Override
    public List<PilotSession> findAllById( Iterable<Long> ids ) { throw new UnsupportedOperationException(); }
    @Override
    public long count() { throw new UnsupportedOperationException(); }
    @Override
    public void deleteById( Long id ) { throw new UnsupportedOperationException(); }
    @Override
    public void delete( PilotSession entity ) { throw new UnsupportedOperationException(); }
    @Override
    public void deleteAllById( Iterable<? extends Long> ids ) { throw new UnsupportedOperationException(); }
    @Override
    public void deleteAll( Iterable<? extends PilotSession> entities ) { throw new UnsupportedOperationException(); }
    @Override
    public void deleteAll() { throw new UnsupportedOperationException(); }
    @Override
    public List<PilotSession> findAll( org.springframework.data.domain.Sort sort ) { throw new UnsupportedOperationException(); }
    @Override
    public org.springframework.data.domain.Page<PilotSession> findAll( org.springframework.data.domain.Pageable pageable ) { throw new UnsupportedOperationException(); }
    @Override
    public void flush() { throw new UnsupportedOperationException(); }
    @Override
    public <S extends PilotSession> S saveAndFlush( S entity ) { throw new UnsupportedOperationException(); }
    @Override
    public <S extends PilotSession> List<S> saveAllAndFlush( Iterable<S> entities ) { throw new UnsupportedOperationException(); }
    @Override
    public void deleteAllInBatch( Iterable<PilotSession> entities ) { throw new UnsupportedOperationException(); }
    @Override
    public void deleteAllByIdInBatch( Iterable<Long> ids ) { throw new UnsupportedOperationException(); }
    @Override
    public void deleteAllInBatch() { throw new UnsupportedOperationException(); }
    @Override
    public PilotSession getOne( Long id ) { throw new UnsupportedOperationException(); }
    @Override
    public PilotSession getById( Long id ) { throw new UnsupportedOperationException(); }
    @Override
    public PilotSession getReferenceById( Long id ) { throw new UnsupportedOperationException(); }
    @Override
    public <S extends PilotSession> Optional<S> findOne( org.springframework.data.domain.Example<S> example ) { throw new UnsupportedOperationException(); }
    @Override
    public <S extends PilotSession> List<S> findAll( org.springframework.data.domain.Example<S> example ) { throw new UnsupportedOperationException(); }
    @Override
    public <S extends PilotSession> List<S> findAll( org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort ) { throw new UnsupportedOperationException(); }
    @Override
    public <S extends PilotSession> org.springframework.data.domain.Page<S> findAll( org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable ) { throw new UnsupportedOperationException(); }
    @Override
    public <S extends PilotSession> long count( org.springframework.data.domain.Example<S> example ) { throw new UnsupportedOperationException(); }
    @Override
    public <S extends PilotSession> boolean exists( org.springframework.data.domain.Example<S> example ) { throw new UnsupportedOperationException(); }
    @Override
    public <S extends PilotSession, R> R findBy( org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction ) { throw new UnsupportedOperationException(); }
}
