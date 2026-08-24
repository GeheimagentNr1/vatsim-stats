package de.secretsoft.vatsim_stats.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table( name = "pilot_session" )
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PilotSession {

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    private Long id;

    @Column( nullable = false )
    private Long cid;

    @Column( nullable = false )
    private String callsign;

    @Column( name = "logon_time", nullable = false )
    private Instant logonTime;

    @Builder.Default
    @Column( name = "sequence_number", nullable = false )
    private int sequenceNumber = 0;

    @Column( name = "planned_departure" )
    private String plannedDeparture;

    @Column( name = "planned_destination" )
    private String plannedDestination;

    @Column( name = "aircraft_short" )
    private String aircraftShort;

    @Enumerated( EnumType.STRING )
    @Column( nullable = false )
    private SessionStatus status;

    @Column( name = "started_at", nullable = false )
    private Instant startedAt;

    @Column( name = "ended_at" )
    private Instant endedAt;
}
