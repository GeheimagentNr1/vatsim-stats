package de.secretsoft.vatsim_stats.referencedata.ourairports;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OurAirportsScheduledImportJob {

    private final OurAirportsImportService importService;

    @Scheduled( cron = "0 30 3 * * *" )
    public void run() {
        try {
            OurAirportsImportResult result = importService.importFromOurAirports();
            log.info( "OurAirports import finished: {} airports, {} runways",
                result.airportsUpserted(), result.runwaysUpserted() );
        } catch( Exception e ) {
            log.error( "OurAirports import failed, keeping previous data", e );
        }
    }
}
