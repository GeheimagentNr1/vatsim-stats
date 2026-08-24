CREATE TABLE airport (
    icao VARCHAR(4) PRIMARY KEY,
    iata VARCHAR(3),
    name VARCHAR(255) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    elevation_ft INTEGER,
    iso_country VARCHAR(2)
);

CREATE TABLE runway (
    id BIGSERIAL PRIMARY KEY,
    airport_icao VARCHAR(4) NOT NULL REFERENCES airport (icao),
    le_ident VARCHAR(8),
    he_ident VARCHAR(8),
    le_latitude DOUBLE PRECISION,
    le_longitude DOUBLE PRECISION,
    he_latitude DOUBLE PRECISION,
    he_longitude DOUBLE PRECISION,
    length_ft INTEGER,
    surface VARCHAR(64)
);

CREATE INDEX idx_runway_airport_icao ON runway (airport_icao);
