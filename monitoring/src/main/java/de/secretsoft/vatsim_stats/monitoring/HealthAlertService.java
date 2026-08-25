package de.secretsoft.vatsim_stats.monitoring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class HealthAlertService {

    private static final Duration VATSIM_POLL_THRESHOLD = Duration.ofMinutes( 5 );
    private static final Duration OURAIRPORTS_IMPORT_THRESHOLD = Duration.ofHours( 30 );

    private final HealthMonitor healthMonitor;
    private final JavaMailSender mailSender;
    private final String alertRecipient;

    public HealthAlertService(
        HealthMonitor healthMonitor,
        JavaMailSender mailSender,
        @Value( "${monitoring.alert.to}" ) String alertRecipient ) {
        this.healthMonitor = healthMonitor;
        this.mailSender = mailSender;
        this.alertRecipient = alertRecipient;
    }

    @Scheduled( fixedRate = 60000 )
    public void checkHealth() {
        check( IngestionHealthListener.VATSIM_POLL_SOURCE, VATSIM_POLL_THRESHOLD );
        check( IngestionHealthListener.OURAIRPORTS_IMPORT_SOURCE, OURAIRPORTS_IMPORT_THRESHOLD );
    }

    private void check( String source, Duration threshold ) {
        Instant now = Instant.now();
        boolean overdue = healthMonitor.isOverdue( source, threshold, now );

        if( overdue && !healthMonitor.isAlerted( source ) ) {
            send( "vatsim-stats: " + source + " is failing",
                source + " has not succeeded within the last " + threshold + ". Check the application logs." );
            healthMonitor.markAlerted( source );
        } else if( !overdue && healthMonitor.isAlerted( source ) ) {
            send( "vatsim-stats: " + source + " has recovered", source + " is succeeding again." );
            healthMonitor.clearAlert( source );
        }
    }

    private void send( String subject, String text ) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo( alertRecipient );
        message.setSubject( subject );
        message.setText( text );
        mailSender.send( message );
    }
}
