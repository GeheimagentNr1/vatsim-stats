package de.secretsoft.vatsim_stats.detection;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class PilotPhaseStateMachine {

    private final PhaseDetectionConfig config;
    private final NearestAirportLookup lookup;

    private Phase phase;
    private String pendingAirportIcao;
    private Instant pendingSince;
    private boolean pendingTouchedDown;
    private String groundAirportIcao;

    public PilotPhaseStateMachine( PhaseDetectionConfig config, NearestAirportLookup lookup ) {
        this.config = config;
        this.lookup = lookup;
        this.phase = null;
    }

    public static PilotPhaseStateMachine reconstruct(
        PhaseDetectionConfig config, NearestAirportLookup lookup, PhaseSnapshot snapshot ) {

        PilotPhaseStateMachine machine = new PilotPhaseStateMachine( config, lookup );
        machine.phase = snapshot.phase();
        machine.pendingAirportIcao = snapshot.pendingAirportIcao();
        machine.pendingSince = snapshot.pendingSince();
        machine.pendingTouchedDown = snapshot.pendingTouchedDown();
        machine.groundAirportIcao = snapshot.groundAirportIcao();
        return machine;
    }

    public List<AirportEvent> process( TrackSample sample ) {
        Optional<AirportRef> nearest =
            lookup.findNearest( sample.latitude(), sample.longitude(), config.nearestAirportRadiusNm() );
        boolean nearGround = nearest.isPresent()
            && sample.altitudeFt() <= nearest.get().elevationFt() + config.altitudeAglThresholdFt();
        boolean belowGroundspeed = sample.groundspeedKt() < config.groundspeedThresholdKt();

        if( phase == null ) {
            phase = nearGround ? Phase.ON_GROUND : Phase.AIRBORNE;
            groundAirportIcao = nearGround ? nearest.get().icao() : null;
            return List.of();
        }

        return switch( phase ) {
            case AIRBORNE -> handleAirborne( sample, nearest, nearGround, belowGroundspeed );
            case GROUND_PENDING -> handleGroundPending( sample, nearGround, belowGroundspeed );
            case ON_GROUND -> handleOnGround( sample, nearGround );
        };
    }

    public List<AirportEvent> onDisappearedFromFeed() {
        if( phase == Phase.GROUND_PENDING ) {
            AirportEvent landing = new AirportEvent( pendingAirportIcao, AirportEventType.LANDING, pendingSince );
            phase = Phase.ON_GROUND;
            groundAirportIcao = pendingAirportIcao;
            clearPending();
            return List.of( landing );
        }
        return List.of();
    }

    public PhaseSnapshot snapshot() {
        return new PhaseSnapshot( phase, pendingAirportIcao, pendingSince, pendingTouchedDown, groundAirportIcao );
    }

    private List<AirportEvent> handleAirborne(
        TrackSample sample, Optional<AirportRef> nearest, boolean nearGround, boolean belowGroundspeed ) {

        if( nearGround ) {
            phase = Phase.GROUND_PENDING;
            pendingAirportIcao = nearest.get().icao();
            pendingSince = sample.timestamp();
            pendingTouchedDown = belowGroundspeed;
        }
        return List.of();
    }

    private List<AirportEvent> handleGroundPending(
        TrackSample sample, boolean nearGround, boolean belowGroundspeed ) {

        if( !nearGround ) {
            AirportEventType type = pendingTouchedDown ? AirportEventType.TOUCH_AND_GO : AirportEventType.LOW_APPROACH;
            AirportEvent event = new AirportEvent( pendingAirportIcao, type, pendingSince );
            phase = Phase.AIRBORNE;
            clearPending();
            return List.of( event );
        }

        pendingTouchedDown = pendingTouchedDown || belowGroundspeed;

        if( !Duration.between( pendingSince, sample.timestamp() ).minus( config.groundDwellThreshold() ).isNegative() ) {
            AirportEvent event = new AirportEvent( pendingAirportIcao, AirportEventType.LANDING, pendingSince );
            groundAirportIcao = pendingAirportIcao;
            phase = Phase.ON_GROUND;
            clearPending();
            return List.of( event );
        }

        return List.of();
    }

    private List<AirportEvent> handleOnGround( TrackSample sample, boolean nearGround ) {
        if( !nearGround ) {
            AirportEvent event = new AirportEvent( groundAirportIcao, AirportEventType.TAKEOFF, sample.timestamp() );
            phase = Phase.AIRBORNE;
            groundAirportIcao = null;
            return List.of( event );
        }
        return List.of();
    }

    private void clearPending() {
        pendingAirportIcao = null;
        pendingSince = null;
        pendingTouchedDown = false;
    }
}
