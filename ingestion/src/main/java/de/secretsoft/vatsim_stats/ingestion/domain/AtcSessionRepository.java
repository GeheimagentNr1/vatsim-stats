package de.secretsoft.vatsim_stats.ingestion.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AtcSessionRepository extends JpaRepository<AtcSession, Long> {

    Optional<AtcSession> findByCidAndCallsignAndLogonTime( Long cid, String callsign, Instant logonTime );

    List<AtcSession> findByEndedAtIsNull();

    List<AtcSession> findTop200ByOrderByStartedAtDesc();
}
