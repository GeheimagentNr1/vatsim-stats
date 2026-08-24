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
@Table( name = "atc_session" )
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtcSession {

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    private Long id;

    @Column( nullable = false )
    private Long cid;

    @Column( nullable = false )
    private String callsign;

    @Column( name = "logon_time", nullable = false )
    private Instant logonTime;

    private Integer facility;

    @Column( name = "started_at", nullable = false )
    private Instant startedAt;

    @Column( name = "ended_at" )
    private Instant endedAt;
}
