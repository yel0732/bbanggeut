ALTER TABLE conversation_topic_state
    ADD COLUMN skipped_targets_json TEXT NULL
        AFTER context_json;

UPDATE conversation_topic_state
SET skipped_targets_json = '[]'
WHERE skipped_targets_json IS NULL;

ALTER TABLE conversation_topic_state
    MODIFY COLUMN skipped_targets_json TEXT NOT NULL;
