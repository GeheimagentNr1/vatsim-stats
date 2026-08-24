package de.secretsoft.vatsim_stats.detection;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PilotPhaseStateMachineTest {

    private static final AirportRef EDDF = new AirportRef( "EDDF", 50.0264, 8.5431, 364 );
    private static final Instant T0 = Instant.parse( "2026-08-24T10:00:00Z" );
    private static final PhaseDetectionConfig CONFIG = PhaseDetectionConfig.defaults();

    private static final NearestAirportLookup ALWAYS_EDDF = ( lat, lon, radius ) -> Optional.of( EDDF );
    private static final NearestAirportLookup NEVER_FOUND = ( lat, lon, radius ) -> Optional.empty();

    private TrackSample sample( int offsetSeconds, double altitudeFt, double groundspeedKt ) {
        return new TrackSample( T0.plusSeconds( offsetSeconds ), 50.0, 8.5, altitudeFt, groundspeedKt );
    }

    @Test
    void firstSampleEstablishesPhaseWithoutEmittingAnEvent() {
        PilotPhaseStateMachine machine = new PilotPhaseStateMachine( CONFIG, ALWAYS_EDDF );

        List<AirportEvent> events = machine.process( sample( 0, 3000, 250 ) );

        assertThat( events ).isEmpty();
    }

    @Test
    void takeoffEmittedImmediatelyWhenLeavingConfirmedGround() {
        PilotPhaseStateMachine machine = new PilotPhaseStateMachine( CONFIG, ALWAYS_EDDF );

        machine.process( sample( 0, 550, 5 ) );
        List<AirportEvent> takeoff = machine.process( sample( 15, 550, 5 ) );
        assertThat( takeoff ).isEmpty();

        List<AirportEvent> events = machine.process( sample( 30, 3000, 180 ) );

        assertThat( events ).containsExactly(
            new AirportEvent( "EDDF", AirportEventType.TAKEOFF, T0.plusSeconds( 30 ) ) );
    }

    @Test
    void landingEmittedAfterGroundDwellThresholdIsReached() {
        PilotPhaseStateMachine machine = new PilotPhaseStateMachine( CONFIG, ALWAYS_EDDF );

        machine.process( sample( 0, 3000, 250 ) );
        machine.process( sample( 15, 3000, 200 ) );

        List<AirportEvent> events = List.of();
        for( int offset = 30; offset <= 105; offset += 15 ) {
            events = machine.process( sample( offset, 550, 15 ) );
            assertThat( events ).as( "offset " + offset ).isEmpty();
        }

        events = machine.process( sample( 120, 550, 15 ) );

        assertThat( events ).containsExactly(
            new AirportEvent( "EDDF", AirportEventType.LANDING, T0.plusSeconds( 30 ) ) );
    }

    @Test
    void touchAndGoEmittedWhenClimbingOutBeforeDwellWithGroundspeedBelowThreshold() {
        PilotPhaseStateMachine machine = new PilotPhaseStateMachine( CONFIG, ALWAYS_EDDF );

        machine.process( sample( 0, 3000, 250 ) );
        machine.process( sample( 15, 550, 15 ) );
        machine.process( sample( 30, 550, 10 ) );

        List<AirportEvent> events = machine.process( sample( 45, 3000, 180 ) );

        assertThat( events ).containsExactly(
            new AirportEvent( "EDDF", AirportEventType.TOUCH_AND_GO, T0.plusSeconds( 15 ) ) );
    }

    @Test
    void lowApproachEmittedWhenClimbingOutBeforeDwellWithGroundspeedNeverBelowThreshold() {
        PilotPhaseStateMachine machine = new PilotPhaseStateMachine( CONFIG, ALWAYS_EDDF );

        machine.process( sample( 0, 3000, 250 ) );
        machine.process( sample( 15, 550, 95 ) );
        machine.process( sample( 30, 550, 90 ) );

        List<AirportEvent> events = machine.process( sample( 45, 3000, 200 ) );

        assertThat( events ).containsExactly(
            new AirportEvent( "EDDF", AirportEventType.LOW_APPROACH, T0.plusSeconds( 15 ) ) );
    }

    @Test
    void disappearingFromFeedWhileGroundPendingEmitsLanding() {
        PilotPhaseStateMachine machine = new PilotPhaseStateMachine( CONFIG, ALWAYS_EDDF );

        machine.process( sample( 0, 3000, 250 ) );
        machine.process( sample( 15, 550, 15 ) );

        List<AirportEvent> events = machine.onDisappearedFromFeed();

        assertThat( events ).containsExactly(
            new AirportEvent( "EDDF", AirportEventType.LANDING, T0.plusSeconds( 15 ) ) );
    }

    @Test
    void noNearbyAirportKeepsAircraftAirborneRegardlessOfAltitudeAndSpeed() {
        PilotPhaseStateMachine machine = new PilotPhaseStateMachine( CONFIG, NEVER_FOUND );

        machine.process( sample( 0, 3000, 250 ) );
        List<AirportEvent> events = machine.process( sample( 15, 50, 5 ) );

        assertThat( events ).isEmpty();
        assertThat( machine.snapshot().phase() ).isEqualTo( Phase.AIRBORNE );
    }

    @Test
    void reconstructResumesFromAPersistedSnapshotWithoutReplayingHistory() {
        PhaseSnapshot snapshot = new PhaseSnapshot(
            Phase.GROUND_PENDING, "EDDF", T0, true, null );
        PilotPhaseStateMachine machine =
            PilotPhaseStateMachine.reconstruct( CONFIG, ALWAYS_EDDF, snapshot );

        List<AirportEvent> events = machine.process( sample( 90, 550, 15 ) );

        assertThat( events ).containsExactly(
            new AirportEvent( "EDDF", AirportEventType.LANDING, T0 ) );
    }
}
