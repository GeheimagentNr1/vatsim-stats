CREATE TABLE pilot_track_point (
    id BIGSERIAL,
    recorded_at TIMESTAMPTZ NOT NULL,
    cid BIGINT NOT NULL,
    callsign VARCHAR(16) NOT NULL,
    logon_time TIMESTAMPTZ NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    altitude_ft INTEGER NOT NULL,
    groundspeed_kt INTEGER NOT NULL,
    heading INTEGER,
    transponder VARCHAR(8),
    qnh_mb INTEGER,
    flight_plan_departure VARCHAR(8),
    flight_plan_destination VARCHAR(8),
    aircraft_short VARCHAR(16),
    PRIMARY KEY (id, recorded_at)
);
SELECT create_hypertable('pilot_track_point', by_range('recorded_at'));
CREATE INDEX idx_pilot_track_point_session
    ON pilot_track_point (cid, callsign, logon_time, recorded_at DESC);

CREATE TABLE atc_snapshot (
    id BIGSERIAL,
    recorded_at TIMESTAMPTZ NOT NULL,
    cid BIGINT NOT NULL,
    callsign VARCHAR(16) NOT NULL,
    logon_time TIMESTAMPTZ NOT NULL,
    frequency VARCHAR(16),
    facility INTEGER,
    visual_range INTEGER,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    PRIMARY KEY (id, recorded_at)
);
SELECT create_hypertable('atc_snapshot', by_range('recorded_at'));
CREATE INDEX idx_atc_snapshot_session
    ON atc_snapshot (cid, callsign, logon_time, recorded_at DESC);

CREATE TABLE pilot_session (
    id BIGSERIAL PRIMARY KEY,
    cid BIGINT NOT NULL,
    callsign VARCHAR(16) NOT NULL,
    logon_time TIMESTAMPTZ NOT NULL,
    sequence_number INTEGER NOT NULL DEFAULT 0,
    planned_departure VARCHAR(8),
    planned_destination VARCHAR(8),
    aircraft_short VARCHAR(16),
    status VARCHAR(16) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    UNIQUE (cid, callsign, logon_time, sequence_number)
);

CREATE TABLE pilot_airport_event (
    id BIGSERIAL PRIMARY KEY,
    pilot_session_id BIGINT NOT NULL REFERENCES pilot_session (id),
    airport_icao VARCHAR(8) NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_pilot_airport_event_session ON pilot_airport_event (pilot_session_id);

CREATE TABLE atc_session (
    id BIGSERIAL PRIMARY KEY,
    cid BIGINT NOT NULL,
    callsign VARCHAR(16) NOT NULL,
    logon_time TIMESTAMPTZ NOT NULL,
    facility INTEGER,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    UNIQUE (cid, callsign, logon_time)
);
