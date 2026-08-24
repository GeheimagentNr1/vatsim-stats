package de.secretsoft.vatsim_stats;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableJpaRepositories(basePackages = "de.secretsoft.vatsim_stats")
@SpringBootApplication(scanBasePackages = "de.secretsoft.vatsim_stats")
public class VatsimStatsApplication {

    public static void main( String[] args ) {
        SpringApplication.run( VatsimStatsApplication.class, args );
    }
}
