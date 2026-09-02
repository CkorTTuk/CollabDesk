ALTER TABLE users
    ADD COLUMN first_name VARCHAR(100) NULL,
    ADD COLUMN last_name VARCHAR(100) NULL,
    ADD COLUMN birth_date DATE NULL,
    ADD COLUMN avatar_key VARCHAR(500) NULL,
    ADD COLUMN email_verified_at DATETIME(6) NULL,
    ADD COLUMN onboarding_completed_at DATETIME(6) NULL;

UPDATE users
SET first_name = display_name,
    email_verified_at = created_at,
    onboarding_completed_at = created_at;

ALTER TABLE auth_identities
    DROP CHECK auth_identities_chk_1,
    ADD CONSTRAINT auth_identities_provider_ck
        CHECK (provider IN ('LOCAL', 'GOOGLE', 'GITHUB'));
