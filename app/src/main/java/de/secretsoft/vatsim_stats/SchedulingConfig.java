package de.secretsoft.vatsim_stats;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the {@code @Scheduled} tasks (VATSIM poll, OurAirports import, health checks).
 * <p>
 * Kept separate from {@link VatsimStatsApplication} and guarded by a property so integration tests
 * can start the full context without a background scheduler firing real poll cycles. Note that
 * Spring Boot 4.0.6 offers no {@code spring.task.scheduling.enabled} flag — {@code @EnableScheduling}
 * itself has to be made conditional.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty( name = "vatsim.scheduling.enabled", havingValue = "true", matchIfMissing = true )
public class SchedulingConfig {
}
