CREATE TABLE users(
    id BIGINT AUTO_INCREMENT,
    email VARCHAR(320) NOT NULL ,
    display_name VARCHAR(100)  NOT NULL ,
    status VARCHAR(20) NOT NULL CHECK ( status in ('ACTIVE', 'DISABLED' )),
    created_at DATETIME(6) NOT NULL ,
    updated_at DATETIME(6)  NOT NULL ,
    version BIGINT NOT NULL ,
    PRIMARY KEY (id),
    UNIQUE (email)
);

CREATE TABLE auth_identities(
    id BIGINT AUTO_INCREMENT,
    user_id BIGINT NOT NULL ,
    provider VARCHAR(20) NOT NULL ,
    provider_subject VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) ,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE (provider, provider_subject),
    CHECK ( provider in ('LOCAL', 'GOOGLE') ),
    CHECK (
            (provider <> 'LOCAL' AND password_hash IS NULL)
               OR
            (provider = 'LOCAL' AND password_hash IS NOT NULL)
         )
);
