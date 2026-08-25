package de.secretsoft.vatsim_stats.ingestion.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PilotTrackPointRepository extends JpaRepository<PilotTrackPoint, Long> {

    List<PilotTrackPoint> findTop10ByCidAndCallsignAndLogonTimeOrderByRecordedAtDesc(
        Long cid, String callsign, Instant logonTime );

    Optional<PilotTrackPoint> findTopByCidAndCallsignAndLogonTimeOrderByRecordedAtDesc(
        Long cid, String callsign, Instant logonTime );
}
