package de.secretsoft.vatsim_stats.ingestion.domain;

import de.secretsoft.vatsim_stats.detection.AirportEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table( name = "pilot_airport_event" )
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PilotAirportEvent {

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    private Long id;

    @ManyToOne( fetch = FetchType.LAZY )
    @JoinColumn( name = "pilot_session_id", nullable = false )
    private PilotSession pilotSession;

    @Column( name = "airport_icao", nullable = false )
    private String airportIcao;

    @Enumerated( EnumType.STRING )
    @Column( name = "event_type", nullable = false )
    private AirportEventType eventType;

    @Column( name = "occurred_at", nullable = false )
    private Instant occurredAt;
}
