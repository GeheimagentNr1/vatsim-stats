-- OurAirports assigns synthetic local identifiers (e.g. "RU-10001", up to 8 chars observed) to
-- airports/airfields that have no official ICAO code, instead of leaving the ident blank.
-- VARCHAR(4) was sized for genuine ICAO codes only and rejects those rows on import.
ALTER TABLE airport ALTER COLUMN icao TYPE VARCHAR(10);
ALTER TABLE runway ALTER COLUMN airport_icao TYPE VARCHAR(10);
