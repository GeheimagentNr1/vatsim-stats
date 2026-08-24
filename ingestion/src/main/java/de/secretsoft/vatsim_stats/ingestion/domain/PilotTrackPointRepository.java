package de.secretsoft.vatsim_stats.ingestion.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PilotTrackPointRepository extends JpaRepository<PilotTrackPoint, Long> {

    List<PilotTrackPoint> findTop10ByCidAndCallsignAndLogonTimeOrderByRecordedAtDesc(
        Long cid, String callsign, Instant logonTime );
}
