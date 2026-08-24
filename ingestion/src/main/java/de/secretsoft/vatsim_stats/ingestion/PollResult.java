package de.secretsoft.vatsim_stats.ingestion;

public record PollResult( int trackPointsSaved, int atcSnapshotsSaved, int recordsSkipped ) {

    public static final PollResult EMPTY = new PollResult( 0, 0, 0 );
}
