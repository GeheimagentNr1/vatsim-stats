-- Supports PilotSessionTimeoutSweeper's per-sweep findByStatus(ACTIVE) scan, which would otherwise
-- be a full sequential scan of pilot_session (an ever-growing table) every 5 minutes.
CREATE INDEX idx_pilot_session_active ON pilot_session (status) WHERE status = 'ACTIVE';
