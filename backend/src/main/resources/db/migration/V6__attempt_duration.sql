ALTER TABLE learning_attempt
    ADD COLUMN duration_seconds INTEGER CHECK (duration_seconds BETWEEN 0 AND 86400);

COMMENT ON COLUMN learning_attempt.duration_seconds IS 'Active problem-solving time recorded for this individual attempt.';
