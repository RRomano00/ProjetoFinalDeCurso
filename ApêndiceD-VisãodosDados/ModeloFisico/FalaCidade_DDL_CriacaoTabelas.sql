-- ============================================================
-- FALA, CIDADE! — MODELO FÍSICO (SQL-DDL)
-- Apêndice D – Visão dos Dados / Modelo Físico
--
-- SGBD: PostgreSQL 18
-- Convenção de nomes: tabelas no SINGULAR, em minúsculas;
-- colunas em snake_case. A tabela "user" é grafada entre aspas
-- duplas porque USER é palavra reservada do padrão SQL.
--
-- O script é idempotente (CREATE/ALTER ... IF [NOT] EXISTS),
-- podendo ser executado em banco novo ou já existente.
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================
-- 0. MIGRAÇÃO DE NOMES (bancos criados antes da padronização)
--    users -> "user"        |  occurrence.users_id -> user_id
-- ============================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = 'public' AND table_name = 'users')
       AND NOT EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = 'public' AND table_name = 'user') THEN
        ALTER TABLE users RENAME TO "user";
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'occurrence'
                 AND column_name = 'users_id') THEN
        ALTER TABLE occurrence RENAME COLUMN users_id TO user_id;
    END IF;
END $$;

-- ============================================================
-- 1. TABELAS DE DOMÍNIO (modelo conceitual — Seção 5.2.2)
-- ============================================================

-- Município de abrangência da ocorrência e de residência do usuário.
CREATE TABLE IF NOT EXISTS city (
    id     SERIAL       PRIMARY KEY,
    name   VARCHAR(100) NOT NULL,
    state  CHAR(2)      NOT NULL,
    UNIQUE (name, state)
);

-- Departamento (secretaria) da prefeitura responsável pelo atendimento.
CREATE TABLE IF NOT EXISTS department (
    id     SERIAL       PRIMARY KEY,
    name   VARCHAR(100) NOT NULL,
    email  VARCHAR(150) NOT NULL UNIQUE,
    -- O departamento é municipal: a ocorrência é encaminhada ao setor do
    -- município do ENDEREÇO dela. Duas prefeituras podem ter uma "Secretaria
    -- de Obras", por isso o nome é único dentro do município, não no sistema.
    city   VARCHAR(100) NOT NULL,
    state  CHAR(2)      NOT NULL,
    UNIQUE (name, city, state)
);

ALTER TABLE department ALTER COLUMN email SET NOT NULL;
ALTER TABLE department ADD COLUMN IF NOT EXISTS city  VARCHAR(100);
ALTER TABLE department ADD COLUMN IF NOT EXISTS state CHAR(2);

-- Bancos criados antes da municipalização: os departamentos existentes são do
-- município de origem do projeto, e a unicidade do nome deixa de ser global.
DO $$
BEGIN
    UPDATE department SET city  = 'Santa Rita do Sapucaí' WHERE city  IS NULL;
    UPDATE department SET state = 'MG'                    WHERE state IS NULL;

    ALTER TABLE department ALTER COLUMN city  SET NOT NULL;
    ALTER TABLE department ALTER COLUMN state SET NOT NULL;

    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'department_name_key') THEN
        ALTER TABLE department DROP CONSTRAINT department_name_key;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'department_email_key') THEN
        ALTER TABLE department ADD CONSTRAINT department_email_key UNIQUE (email);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'department_name_city_state_key') THEN
        ALTER TABLE department ADD CONSTRAINT department_name_city_state_key UNIQUE (name, city, state);
    END IF;
END $$;

-- O leque de encaminhamento lista os setores do município da ocorrência.
CREATE INDEX IF NOT EXISTS idx_department_locality ON department(state, city);


-- Classificação (categoria) da ocorrência e a prioridade que dela decorre (RN04).
CREATE TABLE IF NOT EXISTS classification (
    id             SERIAL       PRIMARY KEY,
    code           VARCHAR(60)  NOT NULL UNIQUE,
    name           VARCHAR(80)  NOT NULL,
    description    VARCHAR(200),
    priority       VARCHAR(10)  NOT NULL DEFAULT 'MEDIA'
                       CHECK (priority IN ('BAIXA','MEDIA','ALTA')),
    requires_sharp_image BOOLEAN NOT NULL DEFAULT FALSE,
    department_id  INTEGER      REFERENCES department(id) ON DELETE SET NULL
);

-- ============================================================
-- 2. USUÁRIO
-- ============================================================
CREATE TABLE IF NOT EXISTS "user" (
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
    state          CHAR(2),
    city_id        INTEGER             REFERENCES city(id) ON DELETE SET NULL,
    department_id  INTEGER             REFERENCES department(id) ON DELETE SET NULL,
    role           VARCHAR(20)         NOT NULL DEFAULT 'CITIZEN'
                       CHECK (role IN ('SUPER_ADMIN','ADMINISTRATOR','EMPLOYEE','CITIZEN')),
    is_active      BOOLEAN             NOT NULL DEFAULT TRUE,
    accepts_terms  BOOLEAN             NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP           NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP           NOT NULL DEFAULT NOW()
);

ALTER TABLE "user" ADD COLUMN IF NOT EXISTS date_of_birth  DATE;
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS phone_number   VARCHAR(20);
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS street         VARCHAR(200);
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS neighborhood   VARCHAR(100);
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS number         VARCHAR(10);
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS cep            VARCHAR(10);
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS city           VARCHAR(100);
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS state          CHAR(2);
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS is_active      BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS accepts_terms  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS created_at     TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS updated_at     TIMESTAMP NOT NULL DEFAULT NOW();

-- Fase 3: vínculos com as entidades de domínio e segundo fator de autenticação (RNF16)
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS city_id           INTEGER REFERENCES city(id) ON DELETE SET NULL;
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS department_id     INTEGER REFERENCES department(id) ON DELETE SET NULL;
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS mfa_enabled       BOOLEAN      NOT NULL DEFAULT FALSE;
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS mfa_secret        VARCHAR(255);
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS mfa_setup_done    BOOLEAN      NOT NULL DEFAULT FALSE;
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS mfa_email_enabled BOOLEAN      NOT NULL DEFAULT FALSE;

-- Fase 4: perfil Super Administrador (RF25). O administrador passou a ser
-- municipal, e quem o nomeia — e define o município dele — é o Super
-- Administrador, único perfil sem recorte territorial. O administrador de
-- origem do projeto é promovido a Super Administrador na migração.
DO $$
DECLARE
    restricao TEXT;
BEGIN
    FOR restricao IN
        SELECT conname FROM pg_constraint
         WHERE conrelid = '"user"'::regclass AND contype = 'c' AND conname LIKE '%role%'
    LOOP
        EXECUTE format('ALTER TABLE "user" DROP CONSTRAINT %I', restricao);
    END LOOP;

    ALTER TABLE "user" ADD CONSTRAINT user_role_check
        CHECK (role IN ('SUPER_ADMIN','ADMINISTRATOR','EMPLOYEE','CITIZEN'));

    UPDATE "user" SET role = 'SUPER_ADMIN'
     WHERE lower(email) = 'admin@falacidade.com' AND role = 'ADMINISTRATOR';
END $$;

-- ============================================================
-- 3. OCORRÊNCIA
-- ============================================================
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
    state                        CHAR(2),
    city_id                      INTEGER      REFERENCES city(id) ON DELETE SET NULL,
    latitude                     DOUBLE PRECISION,
    longitude                    DOUBLE PRECISION,
    url_media                    VARCHAR(500),
    cloudinary_public_id         VARCHAR(255),
    image_blurred                BOOLEAN      NOT NULL DEFAULT FALSE,
    type                         VARCHAR(60)  NOT NULL,
    classification_id            INTEGER      REFERENCES classification(id) ON DELETE SET NULL,
    department_id                INTEGER      REFERENCES department(id) ON DELETE SET NULL,
    status                       VARCHAR(20)  NOT NULL DEFAULT 'PENDENTE'
                                     CHECK (status IN ('PENDENTE','EM_ANDAMENTO','CONCLUIDA','INDEFERIDA')),
    priority                     VARCHAR(10)  NOT NULL DEFAULT 'MEDIA'
                                     CHECK (priority IN ('BAIXA','MEDIA','ALTA')),
    is_anonymous                 BOOLEAN      NOT NULL DEFAULT FALSE,
    anonymous_tracking_code_hash VARCHAR(64),
    -- RNF17: o endereço de rede é gravado apenas sob resumo criptográfico
    -- SHA-256 (64 caracteres hexadecimais), nunca em texto claro.
    ip_address                   VARCHAR(64),
    user_id                      INTEGER      REFERENCES "user"(id) ON DELETE SET NULL,
    group_id                     INTEGER      REFERENCES occurrence(id) ON DELETE SET NULL,
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
ALTER TABLE occurrence ADD COLUMN IF NOT EXISTS ip_address                   VARCHAR(64);
ALTER TABLE occurrence ADD COLUMN IF NOT EXISTS address_reference            VARCHAR(300);
-- RF12: encadeamento de duplicatas — aponta para a ocorrência "raiz" do grupo (50 m + mesma categoria)
ALTER TABLE occurrence ADD COLUMN IF NOT EXISTS group_id                     INTEGER REFERENCES occurrence(id) ON DELETE SET NULL;
-- Fase 3: vínculos com as entidades de domínio do modelo conceitual
-- O município da ocorrência é o do ENDEREÇO dela, não o do cadastro do autor:
-- quem mora em Santa Rita pode registrar um problema em Itajubá. A UF acompanha
-- o município porque há homônimos em estados diferentes (Bom Jesus, Bonito...).
ALTER TABLE occurrence ADD COLUMN IF NOT EXISTS state                        CHAR(2);
ALTER TABLE occurrence ADD COLUMN IF NOT EXISTS city_id                      INTEGER REFERENCES city(id) ON DELETE SET NULL;
ALTER TABLE occurrence ADD COLUMN IF NOT EXISTS classification_id            INTEGER REFERENCES classification(id) ON DELETE SET NULL;
ALTER TABLE occurrence ADD COLUMN IF NOT EXISTS department_id                INTEGER REFERENCES department(id) ON DELETE SET NULL;

-- RNF17: amplia a coluna para acomodar o resumo SHA-256 do endereço de rede
ALTER TABLE occurrence ALTER COLUMN ip_address TYPE VARCHAR(64);

-- RF11: inclui o status INDEFERIDA no CHECK (recria a constraint em bancos já existentes)
ALTER TABLE occurrence DROP CONSTRAINT IF EXISTS occurrence_status_check;

-- O status ATENDIDA passou a se chamar CONCLUIDA. A renomeação vai entre a queda e
-- a recriação do CHECK: antes dela, a constraint antiga recusa o valor novo; depois,
-- a nova recusa o valor antigo. Sem constraint alguma, as duas grafias passam.
UPDATE occurrence SET status = 'CONCLUIDA' WHERE status = 'ATENDIDA';

ALTER TABLE occurrence ADD CONSTRAINT occurrence_status_check
    CHECK (status IN ('PENDENTE','EM_ANDAMENTO','CONCLUIDA','INDEFERIDA'));

-- ============================================================
-- 4. TABELAS DEPENDENTES DA OCORRÊNCIA
-- ============================================================
CREATE TABLE IF NOT EXISTS occurrence_history (
    id             SERIAL PRIMARY KEY,
    occurrence_id  INTEGER     NOT NULL REFERENCES occurrence(id) ON DELETE CASCADE,
    changed_by     INTEGER     REFERENCES "user"(id) ON DELETE SET NULL,
    old_status     VARCHAR(20),
    new_status     VARCHAR(20) NOT NULL,
    observation    TEXT,
    changed_at     TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- RF22: guarda para qual departamento a ocorrência foi encaminhada, de modo que
-- o trâmite fique rastreável mesmo que o departamento seja renomeado depois.
ALTER TABLE occurrence_history ADD COLUMN IF NOT EXISTS department_id
    INTEGER REFERENCES department(id) ON DELETE SET NULL;

-- Mesma renomeação de ATENDIDA para CONCLUIDA na linha do tempo já gravada.
UPDATE occurrence_history SET old_status = 'CONCLUIDA' WHERE old_status = 'ATENDIDA';
UPDATE occurrence_history SET new_status = 'CONCLUIDA' WHERE new_status = 'ATENDIDA';

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
    citizen_id     INTEGER   REFERENCES "user"(id) ON DELETE SET NULL,
    supported_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (occurrence_id, citizen_id)
);

-- ============================================================
-- 5. APOIO À AUTENTICAÇÃO E À COMUNICAÇÃO
-- ============================================================
CREATE TABLE IF NOT EXISTS password_reset_token (
    id          SERIAL PRIMARY KEY,
    user_id     INTEGER      NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
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

-- ============================================================
-- 6. ÍNDICES
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_occurrence_protocol
    ON occurrence(protocol_number);

CREATE INDEX IF NOT EXISTS idx_occurrence_user_id
    ON occurrence(user_id);

CREATE INDEX IF NOT EXISTS idx_occurrence_classification_id
    ON occurrence(classification_id);

-- A triagem do funcionário lista por município do endereço: o índice cobre
-- (state, city) na ordem em que a consulta filtra.
CREATE INDEX IF NOT EXISTS idx_occurrence_locality
    ON occurrence(state, city);

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

-- Cobertura: o município é atendido quando há funcionário ativo cadastrado nele.
CREATE INDEX IF NOT EXISTS idx_user_staff_locality
    ON "user"(state, city)
    WHERE is_active = TRUE AND role IN ('EMPLOYEE','ADMINISTRATOR');

CREATE INDEX IF NOT EXISTS idx_user_mfa_enabled
    ON "user"(mfa_enabled)
    WHERE mfa_enabled = TRUE;

DROP INDEX IF EXISTS idx_occurrence_users_id;
DROP INDEX IF EXISTS idx_users_mfa_enabled;
