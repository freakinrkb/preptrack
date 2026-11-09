-- Keep design confidence distinct from DSA solve independence.
ALTER TABLE learning_attempt
    ADD COLUMN confidence SMALLINT CHECK (confidence BETWEEN 1 AND 5);

ALTER TABLE learning_attempt
    DROP CONSTRAINT attempt_rating_on_completion;

ALTER TABLE learning_attempt
    ADD CONSTRAINT attempt_rating_on_completion
    CHECK (outcome <> 'COMPLETED' OR independence_rating IS NOT NULL OR confidence IS NOT NULL);
