CREATE TABLE verification_challenges (
     id BIGINT AUTO_INCREMENT PRIMARY KEY,
     user_id BIGINT NOT NULL,
     purpose VARCHAR(40) NOT NULL,
     channel VARCHAR(20) NOT NULL,
     destination VARCHAR(320) NOT NULL,
     code_hash VARCHAR(64) NOT NULL,
     issued_at DATETIME(6) NOT NULL,
     expires_at DATETIME(6) NOT NULL,
     consumed_at DATETIME(6) NULL,
     failed_attempts INT NOT NULL DEFAULT 0,
     version BIGINT NOT NULL DEFAULT 0,

     CONSTRAINT fk_verification_challenge_user
         FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
     CONSTRAINT uq_verification_challenge_user_purpose
         UNIQUE (user_id, purpose),
     CONSTRAINT verification_challenge_purpose_ck
         CHECK (purpose IN (
                            'EMAIL_VERIFICATION',
                            'PASSWORD_RESET',
                            'SENSITIVE_ACTION'
             )),
     CONSTRAINT verification_challenge_channel_ck
         CHECK (channel IN ('EMAIL', 'SMS')),
     CONSTRAINT verification_challenge_attempts_ck
         CHECK (failed_attempts >= 0),
     CONSTRAINT verification_challenge_expiry_ck
         CHECK (expires_at > issued_at)
);