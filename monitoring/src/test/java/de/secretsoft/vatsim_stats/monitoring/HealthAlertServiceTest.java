package de.secretsoft.vatsim_stats.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class HealthAlertServiceTest {

    private HealthMonitor healthMonitor;
    private JavaMailSender mailSender;
    private HealthAlertService alertService;

    @BeforeEach
    void setUp() {
        healthMonitor = new HealthMonitor();
        mailSender = mock( JavaMailSender.class );
        alertService = new HealthAlertService( healthMonitor, mailSender, "ops@example.com" );
    }

    @Test
    void doesNotAlertWhileWithinThreshold() {
        healthMonitor.recordSuccess( IngestionHealthListener.VATSIM_POLL_SOURCE, Instant.now() );
        healthMonitor.recordSuccess( IngestionHealthListener.OURAIRPORTS_IMPORT_SOURCE, Instant.now() );

        alertService.checkHealth();

        verify( mailSender, never() ).send( any( SimpleMailMessage.class ) );
    }

    @Test
    void sendsExactlyOneAlertPerFailureEpisode() {
        healthMonitor.recordSuccess( IngestionHealthListener.OURAIRPORTS_IMPORT_SOURCE, Instant.now() );
        healthMonitor.recordSuccess(
            IngestionHealthListener.VATSIM_POLL_SOURCE, Instant.now().minus( java.time.Duration.ofMinutes( 10 ) ) );

        alertService.checkHealth();
        alertService.checkHealth();

        verify( mailSender, times( 1 ) ).send( any( SimpleMailMessage.class ) );
    }

    @Test
    void sendsARecoveryEmailAfterAlertingThenRecovering() {
        healthMonitor.recordSuccess( IngestionHealthListener.OURAIRPORTS_IMPORT_SOURCE, Instant.now() );
        healthMonitor.recordSuccess(
            IngestionHealthListener.VATSIM_POLL_SOURCE, Instant.now().minus( java.time.Duration.ofMinutes( 10 ) ) );
        alertService.checkHealth();

        healthMonitor.recordSuccess( IngestionHealthListener.VATSIM_POLL_SOURCE, Instant.now() );
        alertService.checkHealth();

        verify( mailSender, times( 2 ) ).send( any( SimpleMailMessage.class ) );
    }
}
