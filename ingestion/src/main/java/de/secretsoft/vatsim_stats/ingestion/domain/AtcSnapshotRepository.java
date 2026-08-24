package de.secretsoft.vatsim_stats.ingestion.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AtcSnapshotRepository extends JpaRepository<AtcSnapshot, Long> {
}
