package de.secretsoft.vatsim_stats;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the mechanism the integration tests rely on to keep the background scheduler quiet. Spring
 * Boot 4.0.6 has no {@code spring.task.scheduling.enabled} property, so {@code @EnableScheduling}
 * itself is made conditional; this test pins both directions of that switch.
 */
class SchedulingConfigTest {

    private final ApplicationContextRunner runner =
        new ApplicationContextRunner().withUserConfiguration( SchedulingConfig.class );

    @Test
    void schedulingIsEnabledByDefault() {
        runner.run( context -> assertThat( context ).hasSingleBean( ScheduledAnnotationBeanPostProcessor.class ) );
    }

    @Test
    void schedulingCanBeSwitchedOffForTests() {
        runner.withPropertyValues( "vatsim.scheduling.enabled=false" )
            .run( context -> assertThat( context ).doesNotHaveBean( ScheduledAnnotationBeanPostProcessor.class ) );
    }
}
