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
@Table( name = "atc_snapshot" )
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtcSnapshot {

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

    private String frequency;

    private Integer facility;

    @Column( name = "visual_range" )
    private Integer visualRange;

    private Double latitude;

    private Double longitude;
}
