package de.secretsoft.vatsim_stats.ingestion.vatsimapi;

public class VatsimFeedException extends RuntimeException {

    public VatsimFeedException( String message, Throwable cause ) {
        super( message, cause );
    }
}
