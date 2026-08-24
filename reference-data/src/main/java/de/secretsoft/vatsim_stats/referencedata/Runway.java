package de.secretsoft.vatsim_stats.referencedata;

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

@Entity
@Table( name = "runway" )
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Runway {

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    private Long id;

    @Column( name = "airport_icao", nullable = false )
    private String airportIcao;

    @Column( name = "le_ident" )
    private String leIdent;

    @Column( name = "he_ident" )
    private String heIdent;

    @Column( name = "le_latitude" )
    private Double leLatitude;

    @Column( name = "le_longitude" )
    private Double leLongitude;

    @Column( name = "he_latitude" )
    private Double heLatitude;

    @Column( name = "he_longitude" )
    private Double heLongitude;

    @Column( name = "length_ft" )
    private Integer lengthFt;

    private String surface;
}
