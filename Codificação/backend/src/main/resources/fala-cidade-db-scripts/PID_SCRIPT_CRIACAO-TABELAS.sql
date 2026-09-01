CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    id             SERIAL PRIMARY KEY,
    fullname       VARCHAR(150)        NOT NULL,
    email          VARCHAR(150)        NOT NULL UNIQUE,
    password       VARCHAR(255)        NOT NULL,
    date_of_birth  DATE,
    phone_number   VARCHAR(20),
    street         VARCHAR(200),
    neighborhood   VARCHAR(100),
    number         VARCHAR(10),
    cep            VARCHAR(10),
    city           VARCHAR(100),
    role           VARCHAR(20)         NOT NULL DEFAULT 'CITIZEN'
                       CHECK (role IN ('ADMINISTRATOR','EMPLOYEE','CITIZEN')),
    is_active      BOOLEAN             NOT NULL DEFAULT TRUE,
    accepts_terms  BOOLEAN             NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP           NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP           NOT NULL DEFAULT NOW()
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS date_of_birth  DATE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_number   VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS street         VARCHAR(200);
ALTER TABLE users ADD COLUMN IF NOT EXISTS neighborhood   VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS number         VARCHAR(10);
ALTER TABLE users ADD COLUMN IF NOT EXISTS cep            VARCHAR(10);
ALTER TABLE users ADD COLUMN IF NOT EXISTS city           VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_active      BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS accepts_terms  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_at     TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at     TIMESTAMP NOT NULL DEFAULT NOW();

CREATE TABLE IF NOT EXISTS occurrence (
    id                           SERIAL PRIMARY KEY,
    protocol_number              VARCHAR(20)  NOT NULL UNIQUE,
    title                        VARCHAR(200),
    description                  TEXT         NOT NULL,
    number                       VARCHAR(10),
    street                       VARCHAR(200),
    neighborhood                 VARCHAR(100),
    address_reference            VARCHAR(300),
    city                         VARCHAR(100) NOT NULL,
    latitude                     DOUBLE PRECISION,
    longitude                    DOUBLE PRECISION,
    url_media                    VARCHAR(500),
    cloudinary_public_id         VARCHAR(255),
    image_blurred                BOOLEAN      NOT NULL DEFAULT FALSE,
    type                         VARCHAR(60)  NOT NULL,
    status                       VARCHAR(20)  NOT NULL DEFAULT 'PENDENTE'
                                     CHECK (status IN ('PENDENTE','EM_ANDAMENTO','ATENDIDA','INDEFERIDA')),
    priority                     VARCHAR(10)  NOT NULL DEFAULT 'MEDIA'
                                     CHECK (priority IN ('BAIXA','MEDIA','ALTA')),
    is_anonymous                 BOOLEAN      NOT NULL DEFAULT FALSE,
    anonymous_tracking_code_hash VARCHAR(64),
    ip_address                   VARCHAR(45),
    users_id                     INTEGER      REFERENCES users(id) ON DELETE SET NULL,
    created_at                   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMP    NOT NULL DEFAULT NOW()
);

ALTER TABLE occurrence ADD COLUMN IF NOT EXISTS title                        VARCHAR(200);
ALTER TABLE occurrence ADD COLUMN IF NOT EXISTS latitude                     DOUBLE PRECISION;
ALTER TABLE occurrence ADD COLUMN IF NOT EXISTS longitude                    DOUBLE PRECISION;
ALTER TABLE occurrence ADD COLUMN IF NOT EXISTS cloudinary_public_id         VARCHAR(255);
ALTER TABLE occurrence ADD COLUMN IF NOT EXISTS image_blurred                BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE occurrence ADD COLUMN IF NOT EXISTS priority                     VARCHAR(10) NOT NULL DEFAULT 'MEDIA';
ALTER TABLE occurrence ADD COLUMN IF NOT EXISTS is_anonymous                 BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE occurrence ADD COLUMN IF NOT EXISTS anonymous_tracking_code_hash VARCHAR(64);
ALTER TABLE occurrence ADD COLUMN IF NOT EXISTS updated_at                   TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE occurrence ADD COLUMN IF NOT EXISTS ip_address                   VARCHAR(45);
ALTER TABLE occurrence ADD COLUMN IF NOT EXISTS address_reference            VARCHAR(300);
-- RF12: encadeamento de duplicatas — aponta para a ocorrência "raiz" do grupo (50 m + mesma categoria)
ALTER TABLE occurrence ADD COLUMN IF NOT EXISTS group_id                     INTEGER REFERENCES occurrence(id) ON DELETE SET NULL;

-- RF11: inclui o status INDEFERIDA no CHECK (recria a constraint em bancos já existentes)
ALTER TABLE occurrence DROP CONSTRAINT IF EXISTS occurrence_status_check;
ALTER TABLE occurrence ADD CONSTRAINT occurrence_status_check
    CHECK (status IN ('PENDENTE','EM_ANDAMENTO','ATENDIDA','INDEFERIDA'));

CREATE TABLE IF NOT EXISTS occurrence_history (
    id             SERIAL PRIMARY KEY,
    occurrence_id  INTEGER     NOT NULL REFERENCES occurrence(id) ON DELETE CASCADE,
    changed_by     INTEGER     REFERENCES users(id) ON DELETE SET NULL,
    old_status     VARCHAR(20),
    new_status     VARCHAR(20) NOT NULL,
    observation    TEXT,
    changed_at     TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- RF07: fotos adicionais da ocorrência (a 1ª também fica em occurrence.url_media)
CREATE TABLE IF NOT EXISTS occurrence_media (
    id                    SERIAL PRIMARY KEY,
    occurrence_id         INTEGER      NOT NULL REFERENCES occurrence(id) ON DELETE CASCADE,
    url                   VARCHAR(500) NOT NULL,
    cloudinary_public_id  VARCHAR(255),
    image_blurred         BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS occurrence_support (
    id             SERIAL PRIMARY KEY,
    occurrence_id  INTEGER   NOT NULL REFERENCES occurrence(id) ON DELETE CASCADE,
    citizen_id     INTEGER   REFERENCES users(id) ON DELETE SET NULL,
    supported_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (occurrence_id, citizen_id)
);

CREATE TABLE IF NOT EXISTS password_reset_token (
    id          SERIAL PRIMARY KEY,
    user_id     INTEGER      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMP    NOT NULL,
    used        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS contact_message (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    email       VARCHAR(150) NOT NULL,
    subject     VARCHAR(255),
    message     TEXT         NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_occurrence_protocol
    ON occurrence(protocol_number);

CREATE INDEX IF NOT EXISTS idx_occurrence_users_id
    ON occurrence(users_id);

CREATE INDEX IF NOT EXISTS idx_occurrence_geo
    ON occurrence(latitude, longitude)
    WHERE latitude IS NOT NULL AND longitude IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_history_occurrence_id
    ON occurrence_history(occurrence_id);

CREATE INDEX IF NOT EXISTS idx_reset_token_user_id
    ON password_reset_token(user_id);

CREATE INDEX IF NOT EXISTS idx_occurrence_anon_hash
    ON occurrence(anonymous_tracking_code_hash)
    WHERE anonymous_tracking_code_hash IS NOT NULL;

ALTER TABLE users ADD COLUMN IF NOT EXISTS mfa_enabled       BOOLEAN      NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS mfa_secret        VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS mfa_setup_done    BOOLEAN      NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS mfa_email_enabled BOOLEAN      NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_users_mfa_enabled
    ON users(mfa_enabled)
    WHERE mfa_enabled = TRUE;
