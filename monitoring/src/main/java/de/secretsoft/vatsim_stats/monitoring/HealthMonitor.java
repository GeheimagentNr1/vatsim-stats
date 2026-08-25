package de.secretsoft.vatsim_stats.monitoring;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HealthMonitor {

    private final Map<String, Instant> lastSuccessAt = new ConcurrentHashMap<>();
    private final Map<String, Boolean> alerted = new ConcurrentHashMap<>();

    public void recordSuccess( String source ) {
        recordSuccess( source, Instant.now() );
    }

    public void recordSuccess( String source, Instant at ) {
        lastSuccessAt.put( source, at );
    }

    public boolean isOverdue( String source, Duration threshold, Instant now ) {
        Instant last = lastSuccessAt.get( source );
        return last == null || Duration.between( last, now ).compareTo( threshold ) > 0;
    }

    public boolean isAlerted( String source ) {
        return alerted.getOrDefault( source, false );
    }

    public void markAlerted( String source ) {
        alerted.put( source, true );
    }

    public void clearAlert( String source ) {
        alerted.put( source, false );
    }
}
