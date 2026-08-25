package de.secretsoft.vatsim_stats.ingestion.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PilotTrackPointRepository extends JpaRepository<PilotTrackPoint, Long> {

    List<PilotTrackPoint> findTop10ByCidAndCallsignAndLogonTimeOrderByRecordedAtDesc(
        Long cid, String callsign, Instant logonTime );

    /**
     * Bounded to track points recorded at or after {@code recordedAtFrom}. {@code pilot_track_point} has no
     * {@code sequence_number} column, so multiple legs under the same (cid, callsign, logonTime)
     * natural key share one pool of raw points; bounding by a leg's own {@code startedAt} prevents
     * ever attributing an earlier, already-completed leg's track points to a later, still-active one.
     */
    Optional<PilotTrackPoint> findTopByCidAndCallsignAndLogonTimeAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(
        Long cid, String callsign, Instant logonTime, Instant recordedAtFrom );
}
