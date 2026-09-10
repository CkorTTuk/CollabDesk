ALTER TABLE users
    ADD COLUMN preferred_locale VARCHAR(10) NOT NULL DEFAULT 'en';

ALTER TABLE users
    ADD CONSTRAINT users_preferred_locale_ck
        CHECK (preferred_locale IN ('en', 'ru', 'sk'));