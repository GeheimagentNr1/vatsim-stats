package de.secretsoft.vatsim_stats;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "de.secretsoft.vatsim_stats")
public class VatsimStatsApplication {

    public static void main( String[] args ) {
        SpringApplication.run( VatsimStatsApplication.class, args );
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
