package de.secretsoft.vatsim_stats.ingestion.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PilotSessionRepository extends JpaRepository<PilotSession, Long> {

    Optional<PilotSession> findByCidAndCallsignAndLogonTimeAndSequenceNumber(
        Long cid, String callsign, Instant logonTime, int sequenceNumber );

    Optional<PilotSession> findFirstByCidAndCallsignAndLogonTimeOrderBySequenceNumberDesc(
        Long cid, String callsign, Instant logonTime );

    List<PilotSession> findByStatus( SessionStatus status );
}
