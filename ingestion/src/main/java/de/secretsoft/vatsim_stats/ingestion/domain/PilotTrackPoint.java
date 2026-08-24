package de.secretsoft.vatsim_stats.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table( name = "pilot_track_point" )
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PilotTrackPoint {

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    private Long id;

    @Column( name = "recorded_at", nullable = false )
    private Instant recordedAt;

    @Column( nullable = false )
    private Long cid;

    @Column( nullable = false )
    private String callsign;

    @Column( name = "logon_time", nullable = false )
    private Instant logonTime;

    @Column( nullable = false )
    private double latitude;

    @Column( nullable = false )
    private double longitude;

    @Column( name = "altitude_ft", nullable = false )
    private int altitudeFt;

    @Column( name = "groundspeed_kt", nullable = false )
    private int groundspeedKt;

    private Integer heading;

    private String transponder;

    @Column( name = "qnh_mb" )
    private Integer qnhMb;

    @Column( name = "flight_plan_departure" )
    private String flightPlanDeparture;

    @Column( name = "flight_plan_destination" )
    private String flightPlanDestination;

    @Column( name = "aircraft_short" )
    private String aircraftShort;
}
