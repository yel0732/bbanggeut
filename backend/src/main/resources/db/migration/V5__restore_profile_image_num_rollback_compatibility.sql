-- Keep the schema compatible with the immediately previous backend image.
-- Jenkins can then roll the application back after V4 has already migrated the DB.

ALTER TABLE parent
    ADD COLUMN profile_image_num INT NOT NULL DEFAULT 1 AFTER profile_image_key;

ALTER TABLE child
    ADD COLUMN profile_image_num INT NOT NULL DEFAULT 1 AFTER profile_image_key;

UPDATE child
SET profile_image_num = CASE profile_image_key
    WHEN 'profile-presets/child-avatar-tiger' THEN 2
    WHEN 'profile-presets/child-avatar-rabbit' THEN 3
    ELSE 1
END;
