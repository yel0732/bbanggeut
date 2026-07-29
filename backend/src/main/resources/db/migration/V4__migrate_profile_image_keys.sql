-- Replace the legacy numeric profile image selection with stable profile image keys.
-- Existing child selections keep their meaning: 1=default, 2=tiger, 3=rabbit.

ALTER TABLE parent
    ADD COLUMN profile_image_key VARCHAR(512) NULL AFTER pin_set_at;

UPDATE parent
SET profile_image_key = 'profile-presets/parent-avatar';

ALTER TABLE parent
    MODIFY COLUMN profile_image_key VARCHAR(512) NOT NULL
        DEFAULT 'profile-presets/parent-avatar',
    DROP COLUMN profile_image_num;

ALTER TABLE child
    ADD COLUMN profile_image_key VARCHAR(512) NULL AFTER name;

UPDATE child
SET profile_image_key = CASE profile_image_num
    WHEN 2 THEN 'profile-presets/child-avatar-tiger'
    WHEN 3 THEN 'profile-presets/child-avatar-rabbit'
    ELSE 'profile-presets/child-avatar'
END;

ALTER TABLE child
    MODIFY COLUMN profile_image_key VARCHAR(512) NOT NULL
        DEFAULT 'profile-presets/child-avatar',
    DROP COLUMN profile_image_num;
