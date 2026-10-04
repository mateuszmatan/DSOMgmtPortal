--liquibase formatted sql

-- Pipelines, their keys and the configuration published for the DevSecOps library.

--changeset dso-portal:002-pipeline dbms:mysql
CREATE TABLE DSO_PIPELINE (
    ID                          BIGINT          NOT NULL AUTO_INCREMENT,
    SERVICE_ID                  BIGINT          NOT NULL,
    PIPELINE_TYPE               VARCHAR(20)     NOT NULL,
    AGENT_LABELS                TEXT            NOT NULL,
    EXTENDED_PIPELINE_JOB       TEXT,
    SECURITY_PIPELINE_JOB       TEXT,
    JENKINS_JOB                 TEXT,
    DESCRIPTION                 TEXT,
    CREATED_AT                  DATETIME(6)     NOT NULL,
    UPDATED_AT                  DATETIME(6)     NOT NULL,
    VERSION                     BIGINT          DEFAULT 0 NOT NULL,
    CONSTRAINT PK_DSO_PIPELINE PRIMARY KEY (ID),
    CONSTRAINT FK_DSO_PIPELINE_SERVICE FOREIGN KEY (SERVICE_ID) REFERENCES DSO_SERVICE (ID) ON DELETE CASCADE,
    CONSTRAINT UK_DSO_PIPELINE_TYPE UNIQUE (SERVICE_ID, PIPELINE_TYPE),
    CONSTRAINT CK_DSO_PIPELINE_TYPE CHECK (PIPELINE_TYPE IN ('FULL', 'SECURITY', 'EXTENDED', 'SAST'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
--rollback DROP TABLE DSO_PIPELINE;

-- One row per key ever issued; a pipeline has at most one ACTIVE key, enforced by the application
-- under a row lock on DSO_PIPELINE. Revoked keys are kept as the audit trail.
--changeset dso-portal:002-pipeline-key dbms:mysql
CREATE TABLE DSO_PIPELINE_KEY (
    ID                          BIGINT          NOT NULL AUTO_INCREMENT,
    PIPELINE_ID                 BIGINT          NOT NULL,
    KEY_VALUE                   VARCHAR(36)     NOT NULL,
    STATUS                      VARCHAR(20)     NOT NULL,
    ISSUED_AT                   DATETIME(6)     NOT NULL,
    REVOKED_AT                  DATETIME(6),
    REVOKE_REASON               TEXT,
    LAST_USED_AT                DATETIME(6),
    CONSTRAINT PK_DSO_PIPELINE_KEY PRIMARY KEY (ID),
    CONSTRAINT FK_DSO_PIPELINE_KEY_PIPELINE FOREIGN KEY (PIPELINE_ID) REFERENCES DSO_PIPELINE (ID) ON DELETE CASCADE,
    CONSTRAINT UK_DSO_PIPELINE_KEY_VALUE UNIQUE (KEY_VALUE),
    CONSTRAINT CK_DSO_PIPELINE_KEY_STATUS CHECK (
        (STATUS = 'ACTIVE' AND REVOKED_AT IS NULL) OR (STATUS = 'REVOKED' AND REVOKED_AT IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX IX_DSO_PIPELINE_KEY_PIPELINE ON DSO_PIPELINE_KEY (PIPELINE_ID, STATUS);
--rollback DROP TABLE DSO_PIPELINE_KEY;

-- The complete configuration of each pipeline as JSON, rendered by the portal whenever the pipeline, its product
-- or the global settings change.
--changeset dso-portal:002-pipeline-config dbms:mysql
CREATE TABLE DSO_PIPELINE_CONFIG (
    PIPELINE_ID                 BIGINT          NOT NULL,
    CONFIG_JSON                 LONGTEXT        NOT NULL,
    RENDERED_AT                 DATETIME(6)     NOT NULL,
    CONSTRAINT PK_DSO_PIPELINE_CONFIG PRIMARY KEY (PIPELINE_ID),
    CONSTRAINT FK_DSO_PIPELINE_CONFIG_PIPELINE FOREIGN KEY (PIPELINE_ID)
        REFERENCES DSO_PIPELINE (ID) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
--rollback DROP TABLE DSO_PIPELINE_CONFIG;

-- What the DevSecOps library reads with its read-only database user: the configuration of the pipeline a key
-- belongs to. A revoked key returns its reason and no configuration.
--changeset dso-portal:002-library-config-view dbms:mysql
CREATE VIEW DSO_LIBRARY_CONFIG_V AS
SELECT k.KEY_VALUE      AS PIPELINE_KEY,
       k.STATUS         AS KEY_STATUS,
       k.REVOKE_REASON  AS REVOKE_REASON,
       c.CONFIG_JSON    AS CONFIG_JSON,
       c.RENDERED_AT    AS RENDERED_AT
  FROM DSO_PIPELINE_KEY k
  LEFT JOIN DSO_PIPELINE_CONFIG c ON c.PIPELINE_ID = k.PIPELINE_ID AND k.STATUS = 'ACTIVE';
--rollback DROP VIEW DSO_LIBRARY_CONFIG_V;
