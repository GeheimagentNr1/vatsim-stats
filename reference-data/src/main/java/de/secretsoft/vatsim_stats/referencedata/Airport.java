package de.secretsoft.vatsim_stats.referencedata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table( name = "airport" )
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Airport {

    @Id
    private String icao;

    private String iata;

    @Column( nullable = false )
    private String name;

    @Column( nullable = false )
    private double latitude;

    @Column( nullable = false )
    private double longitude;

    @Column( name = "elevation_ft" )
    private Integer elevationFt;

    @Column( name = "iso_country" )
    private String isoCountry;
}
