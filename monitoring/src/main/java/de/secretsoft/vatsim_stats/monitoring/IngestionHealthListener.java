package de.secretsoft.vatsim_stats.monitoring;

import de.secretsoft.vatsim_stats.ingestion.PollCycleSucceededEvent;
import de.secretsoft.vatsim_stats.referencedata.ourairports.OurAirportsImportSucceededEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IngestionHealthListener {

    public static final String VATSIM_POLL_SOURCE = "vatsim-poll";
    public static final String OURAIRPORTS_IMPORT_SOURCE = "ourairports-import";

    private final HealthMonitor healthMonitor;

    @EventListener
    public void onPollCycleSucceeded( PollCycleSucceededEvent event ) {
        healthMonitor.recordSuccess( VATSIM_POLL_SOURCE, event.occurredAt() );
    }

    @EventListener
    public void onOurAirportsImportSucceeded( OurAirportsImportSucceededEvent event ) {
        healthMonitor.recordSuccess( OURAIRPORTS_IMPORT_SOURCE, event.occurredAt() );
    }
}
