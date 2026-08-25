package de.secretsoft.vatsim_stats.ingestion.session;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks, per key, how many consecutive poll cycles a key has been absent, and decides when that
 * absence should be treated as a genuine disappearance rather than a single transient VATSIM feed
 * gap. Shared by {@link PilotSessionOrchestrator} (pilots) and {@link AtcSessionTracker} (ATC
 * controllers) — see the spec's "Verschwinden-Erkennung mit Pufferzeit" section.
 */
class DisappearanceDebounce<K> {

    private final int thresholdCycles;
    private final ConcurrentMap<K, Integer> missedCycles = new ConcurrentHashMap<>();

    DisappearanceDebounce( int thresholdCycles ) {
        this.thresholdCycles = thresholdCycles;
    }

    /** Call when {@code key} was present in the current cycle. Resets its miss count to zero. */
    void seen( K key ) {
        missedCycles.remove( key );
    }

    /**
     * Call when {@code key} was absent from the current cycle. Returns {@code true} once the key has
     * been absent for {@code thresholdCycles} consecutive calls without an intervening {@link #seen},
     * clearing the internal counter in that case so a later reappearance starts counting from zero.
     */
    boolean recordMissAndCheckThresholdReached( K key ) {
        int misses = missedCycles.merge( key, 1, Integer::sum );
        if( misses < thresholdCycles ) {
            return false;
        }
        missedCycles.remove( key );
        return true;
    }
}
