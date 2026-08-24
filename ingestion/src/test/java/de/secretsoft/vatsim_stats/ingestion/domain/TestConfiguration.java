package de.secretsoft.vatsim_stats.ingestion.domain;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;

@Configuration
@SpringBootApplication(
    scanBasePackages = {
        "de.secretsoft.vatsim_stats.ingestion",
        "de.secretsoft.vatsim_stats.detection"
    }
)
public class TestConfiguration {
}
