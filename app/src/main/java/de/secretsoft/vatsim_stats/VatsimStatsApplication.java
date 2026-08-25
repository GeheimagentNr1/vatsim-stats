package de.secretsoft.vatsim_stats;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

// Scheduling lives in SchedulingConfig so it can be switched off in tests.
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
