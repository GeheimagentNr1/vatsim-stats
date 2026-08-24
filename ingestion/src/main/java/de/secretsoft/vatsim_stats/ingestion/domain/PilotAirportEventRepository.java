package de.secretsoft.vatsim_stats.ingestion.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PilotAirportEventRepository extends JpaRepository<PilotAirportEvent, Long> {
}
