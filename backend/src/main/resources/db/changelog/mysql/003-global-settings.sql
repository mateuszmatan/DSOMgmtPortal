--liquibase formatted sql

-- The settings every pipeline shares, which services cannot override: one row, created by the portal at start-up
-- with BBH's current values.

--changeset dso-portal:003-global-settings dbms:mysql
CREATE TABLE DSO_GLOBAL_SETTINGS (
    ID                              BIGINT          NOT NULL,
    -- tools and platform
    JENKINS_URL                     TEXT,
    JENKINS_LIBRARY                 VARCHAR(200)    NOT NULL,
    ASOC_URL                        TEXT            NOT NULL,
    APPSCAN_CLIENT_LINUX_URL        TEXT            NOT NULL,
    APPSCAN_CLIENT_WINDOWS_URL      TEXT            NOT NULL,
    PROXY_HOST                      TEXT,
    PROXY_PORT                      INT,
    PROXY_USER                      VARCHAR(100),
    OIS_HOST                        TEXT,
    SONAR_SERVER_URL                TEXT            NOT NULL,
    SONAR_INSTALLATION_NAME         VARCHAR(200)    NOT NULL,
    NEXUS_IQ_SERVER_URL             TEXT            NOT NULL,
    NEXUS_IQ_CREDENTIALS_ID         VARCHAR(200)    NOT NULL,
    NEXUS_SNAPSHOT_URL              TEXT,
    NEXUS_SNAPSHOT_REPOSITORY_ID    VARCHAR(200),
    INFLUX_WRITE_URL                TEXT,
    INFLUX_CREDENTIALS_ID           VARCHAR(200),
    IOS_BUILD_AGENT                 TEXT,
    -- deployment defaults
    UCD_SITE_NAME                   VARCHAR(200)    NOT NULL,
    UCD_DEPLOY_PROCESS              VARCHAR(200)    NOT NULL,
    RD_HOST                         TEXT            NOT NULL,
    QC_HOST                         TEXT            NOT NULL,
    SSH_USER                        VARCHAR(100)    NOT NULL,
    DEPLOY_SCRIPT                   TEXT            NOT NULL,
    VERSION_FILE                    TEXT            NOT NULL,
    -- scan policy (the severity limits are in DSO_GLOBAL_SEVERITY_LIMIT)
    COVERAGE_MIN_LINE               INT             NOT NULL,
    SAST_PREPARE_TIMEOUT_MIN        INT             NOT NULL,
    SAST_POLL_TIMEOUT_MIN           INT             NOT NULL,
    SAST_POLL_INTERVAL_SEC          INT             NOT NULL,
    SCA_ENABLED                     INT             NOT NULL,
    SCA_POLL_TIMEOUT_MIN            INT             NOT NULL,
    SCA_POLL_INTERVAL_SEC           INT             NOT NULL,
    DAST_POLL_TIMEOUT_MIN           INT             NOT NULL,
    DAST_POLL_INTERVAL_SEC          INT             NOT NULL,
    DAST_REPORT_TIMEOUT_MIN         INT             NOT NULL,
    DAST_REPORT_INTERVAL_SEC        INT             NOT NULL,
    SONAR_WAIT_FOR_QUALITY_GATE     INT             NOT NULL,
    SONAR_QUALITY_GATE_TIMEOUT_MIN  INT             NOT NULL,
    -- release gate
    RELEASE_GATE_SCANNERS           VARCHAR(100),
    RELEASE_GATE_REQUIRE_COVERAGE   INT             NOT NULL,
    RELEASE_GATE_STATE_FILE         VARCHAR(200)    NOT NULL,
    -- defaults a service may override
    DEFAULT_BUILD_TOOL              VARCHAR(20)     NOT NULL,
    DEFAULT_DEPLOY_TARGET           VARCHAR(20)     NOT NULL,
    DEFAULT_SOURCE_DIR              TEXT            NOT NULL,
    TESTS_MAX_PARALLEL              INT             NOT NULL,
    GOLDEN_FIX_ENABLED              INT             NOT NULL,
    GOLDEN_FIX_DIRECT_ONLY          INT,
    GOLDEN_FIX_MIN_THREAT_LEVEL     INT,
    GOLDEN_FIX_ECOSYSTEMS           VARCHAR(200),
    GOLDEN_FIX_VERSION_TYPES        TEXT,
    GOLDEN_FIX_EXCLUDE_DIRS         TEXT,
    GOLDEN_FIX_VERIFY               INT,
    GOLDEN_FIX_VERIFY_ATTEMPTS      INT,
    GOLDEN_FIX_VERIFY_TIMEOUT       INT,
    GOLDEN_FIX_VERIFY_MAVEN         TEXT,
    GOLDEN_FIX_VERIFY_GRADLE        TEXT,
    GOLDEN_FIX_VERIFY_NPM           TEXT,
    GOLDEN_FIX_VERIFY_PIP           TEXT,
    GOLDEN_FIX_VERIFY_PUB           TEXT,
    GOLDEN_FIX_AUTHOR_NAME          VARCHAR(200),
    GOLDEN_FIX_AUTHOR_EMAIL         TEXT,
    GOLDEN_FIX_TIME_ZONE            VARCHAR(100),
    CREATED_AT                      DATETIME(6)     NOT NULL,
    UPDATED_AT                      DATETIME(6)     NOT NULL,
    VERSION                         BIGINT          DEFAULT 0 NOT NULL,
    CONSTRAINT PK_DSO_GLOBAL_SETTINGS PRIMARY KEY (ID),
    CONSTRAINT CK_DSO_GLOBAL_SETTINGS_ROW CHECK (ID = 1),
    CONSTRAINT CK_DSO_GLOBAL_BUILD_TOOL CHECK (DEFAULT_BUILD_TOOL IN ('GRADLE', 'MAVEN', 'FLUTTER')),
    CONSTRAINT CK_DSO_GLOBAL_DEPLOY_TARGET CHECK (DEFAULT_DEPLOY_TARGET IN ('VM', 'OPENSHIFT')),
    CONSTRAINT CK_DSO_GLOBAL_FLAGS CHECK (SCA_ENABLED IN (0, 1) AND SONAR_WAIT_FOR_QUALITY_GATE IN (0, 1)
        AND RELEASE_GATE_REQUIRE_COVERAGE IN (0, 1) AND GOLDEN_FIX_ENABLED IN (0, 1)
        AND GOLDEN_FIX_DIRECT_ONLY IN (0, 1) AND GOLDEN_FIX_VERIFY IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
--rollback DROP TABLE DSO_GLOBAL_SETTINGS;

--changeset dso-portal:003-global-severity-limit dbms:mysql
CREATE TABLE DSO_GLOBAL_SEVERITY_LIMIT (
    SETTINGS_ID                 BIGINT          NOT NULL,
    SCANNER                     VARCHAR(20)     NOT NULL,
    MAX_CRITICAL                INT             NOT NULL,
    MAX_HIGH                    INT             NOT NULL,
    MAX_MEDIUM                  INT             NOT NULL,
    CONSTRAINT PK_DSO_GLOBAL_SEVERITY_LIMIT PRIMARY KEY (SETTINGS_ID, SCANNER),
    CONSTRAINT FK_DSO_SEVERITY_LIMIT_SETTINGS FOREIGN KEY (SETTINGS_ID)
        REFERENCES DSO_GLOBAL_SETTINGS (ID) ON DELETE CASCADE,
    CONSTRAINT CK_DSO_SEVERITY_LIMIT_SCANNER CHECK (SCANNER IN ('SAST', 'SCA', 'NEXUS_IQ', 'DAST')),
    CONSTRAINT CK_DSO_SEVERITY_LIMIT_VALUES CHECK (MAX_CRITICAL >= 0 AND MAX_HIGH >= 0 AND MAX_MEDIUM >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
--rollback DROP TABLE DSO_GLOBAL_SEVERITY_LIMIT;
