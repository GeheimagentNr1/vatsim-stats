package de.secretsoft.vatsim_stats.ingestion.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PilotAirportEventRepository extends JpaRepository<PilotAirportEvent, Long> {

    List<PilotAirportEvent> findByPilotSessionOrderByOccurredAt( PilotSession pilotSession );
}
