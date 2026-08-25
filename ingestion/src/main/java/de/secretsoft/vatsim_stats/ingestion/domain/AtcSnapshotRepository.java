package de.secretsoft.vatsim_stats.ingestion.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface AtcSnapshotRepository extends JpaRepository<AtcSnapshot, Long> {

    Optional<AtcSnapshot> findTopByCidAndCallsignAndLogonTimeOrderByRecordedAtDesc(
        Long cid, String callsign, Instant logonTime );
}
