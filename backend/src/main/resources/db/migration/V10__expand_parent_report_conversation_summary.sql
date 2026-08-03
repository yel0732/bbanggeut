ALTER TABLE report_conversation_summary
    ADD COLUMN emotion_details TEXT NULL AFTER emotions,
    ADD COLUMN notable_quotes TEXT NULL AFTER interests,
    ADD COLUMN conversation_guide TEXT NULL AFTER notable_quotes;

UPDATE report_conversation_summary
SET emotion_details = '[]',
    notable_quotes = '[]',
    conversation_guide = '{"description":"","questions":[]}'
WHERE emotion_details IS NULL
   OR notable_quotes IS NULL
   OR conversation_guide IS NULL;

ALTER TABLE report_conversation_summary
    MODIFY COLUMN emotion_details TEXT NOT NULL,
    MODIFY COLUMN notable_quotes TEXT NOT NULL,
    MODIFY COLUMN conversation_guide TEXT NOT NULL;
