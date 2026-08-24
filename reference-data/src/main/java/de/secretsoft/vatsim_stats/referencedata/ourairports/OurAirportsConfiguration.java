package de.secretsoft.vatsim_stats.referencedata.ourairports;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OurAirportsConfiguration {

    @Bean
    public OurAirportsCsvParser ourAirportsCsvParser() {
        return new OurAirportsCsvParser();
    }
}
