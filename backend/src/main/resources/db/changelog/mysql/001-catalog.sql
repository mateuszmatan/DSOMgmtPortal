--liquibase formatted sql

-- Products and their services: every setting the DevSecOps library reads for a service, one changeset per table.
-- Written for MySQL 8.0.16 and later (local runs); mirrors the Oracle changelog of the same name, keep the two in step.
-- Long text columns are TEXT: as VARCHAR they would exceed MySQL's 65,535 byte row limit in utf8mb4.

--changeset dso-portal:001-product dbms:mysql
CREATE TABLE DSO_PRODUCT (
    ID                          BIGINT          NOT NULL AUTO_INCREMENT,
    CODE                        VARCHAR(50)     NOT NULL,
    NAME                        VARCHAR(200)    NOT NULL,
    DESCRIPTION                 TEXT,
    OWNER_TEAM                  VARCHAR(200),
    CONTACT_EMAIL               TEXT,
    ASOC_KEY_ID                 VARCHAR(200)    NOT NULL,
    ASOC_SECRET_CREDENTIALS_ID  VARCHAR(200),
    CREATED_AT                  DATETIME(6)     NOT NULL,
    UPDATED_AT                  DATETIME(6)     NOT NULL,
    VERSION                     BIGINT          DEFAULT 0 NOT NULL,
    CONSTRAINT PK_DSO_PRODUCT PRIMARY KEY (ID),
    CONSTRAINT UK_DSO_PRODUCT_CODE UNIQUE (CODE),
    CONSTRAINT UK_DSO_PRODUCT_NAME UNIQUE (NAME)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
--rollback DROP TABLE DSO_PRODUCT;

--changeset dso-portal:001-service dbms:mysql
CREATE TABLE DSO_SERVICE (
    ID                                  BIGINT          NOT NULL AUTO_INCREMENT,
    PRODUCT_ID                          BIGINT          NOT NULL,
    NAME                                VARCHAR(100)    NOT NULL,
    DESCRIPTION                         TEXT,
    DISPLAY_ORDER                       INT             DEFAULT 0 NOT NULL,
    -- build
    BUILD_TOOL                          VARCHAR(20)     NOT NULL,
    SOURCE_DIR                          TEXT            NOT NULL,
    JAVA_PATH                           TEXT,
    BUILD_TOOL_AUTO_SETUP               INT             DEFAULT 0 NOT NULL,
    BUILD_PATH                          TEXT,
    BUILD_TASKS                         TEXT,
    BUILD_FLAGS                         TEXT,
    BUILD_DIRECTORY                     TEXT,
    BUILD_MAVEN_HOME                    TEXT,
    BUILD_ENVIRONMENT                   TEXT,
    -- unit tests and coverage
    UNIT_TEST_TASKS                     TEXT,
    UNIT_TEST_FLAGS                     TEXT,
    UNIT_TEST_DIRECTORY                 TEXT,
    UNIT_TEST_MAVEN_HOME                TEXT,
    UNIT_TEST_ENVIRONMENT               TEXT,
    UNIT_TEST_RESULTS                   TEXT,
    UNIT_TEST_ROOT_DIR                  TEXT,
    UNIT_TEST_REPORT_DIR                TEXT,
    UNIT_TEST_ALLOW_EMPTY               INT             DEFAULT 0 NOT NULL,
    COVERAGE_REPORT_PATH                TEXT,
    -- smoke, regression and performance test runs (the jobs are in DSO_SERVICE_TEST_JOB)
    TESTS_MAX_PARALLEL                  INT,
    SMOKE_MAX_PARALLEL                  INT,
    REGRESSION_MAX_PARALLEL             INT,
    PERFORMANCE_MAX_PARALLEL            INT,
    -- deployment
    DEPLOY_TARGET                       VARCHAR(20)     NOT NULL,
    APP_NAME                            VARCHAR(200),
    ARTIFACT_NAME                       TEXT,
    BASE_ARTIFACT_NAME                  TEXT,
    DELIVERY_TASKS                      TEXT,
    DELIVERY_FLAGS                      TEXT,
    DELIVERY_DIRECTORY                  TEXT,
    DELIVERY_MAVEN_HOME                 TEXT,
    DELIVERY_ENVIRONMENT                TEXT,
    -- UrbanCode Deploy (the applications are in DSO_UCD_APPLICATION)
    UCD_SITE_NAME                       VARCHAR(200),
    UCD_DEPLOY_PROCESS                  VARCHAR(200),
    UCD_SKIP_WAIT                       INT             DEFAULT 0 NOT NULL,
    UCD_DEPLOY_WITH_SNAPSHOT            INT             DEFAULT 1 NOT NULL,
    UCD_UPDATE_SNAPSHOT_COMPONENTS      INT             DEFAULT 0 NOT NULL,
    UCD_INCLUDE_ONLY_DEPLOY_VERSIONS    INT             DEFAULT 1 NOT NULL,
    UCD_DEPLOY_ONLY_CHANGED             INT             DEFAULT 0 NOT NULL,
    UCD_DEPLOY_DESCRIPTION              TEXT,
    UCD_REQUEST_PROPERTIES              TEXT,
    -- HCL AppScan: SAST and DAST
    APPSCAN_APP_ID                      VARCHAR(36)     NOT NULL,
    SAST_SCAN_NAME                      VARCHAR(200),
    SAST_INCLUDED_DIRS                  TEXT,
    SAST_EXCLUDED_DIRS                  TEXT,
    APPSCAN_COMPILE                     INT             DEFAULT 1 NOT NULL,
    APPSCAN_SOURCE_CODE_ONLY            INT             DEFAULT 0 NOT NULL,
    APPSCAN_USE_CONFIG_FILE             INT             DEFAULT 0 NOT NULL,
    APPSCAN_INSECURE_TLS                INT             DEFAULT 0 NOT NULL,
    APPSCAN_CLIENT_PATH                 TEXT,
    APPSCAN_COMPILE_TASKS               TEXT,
    APPSCAN_COMPILE_FLAGS               TEXT,
    APPSCAN_COMPILE_DIRECTORY           TEXT,
    APPSCAN_COMPILE_MAVEN_HOME          TEXT,
    APPSCAN_COMPILE_ENVIRONMENT         TEXT,
    DAST_ENABLED                        INT             DEFAULT 0 NOT NULL,
    DAST_SCAN_NAME                      VARCHAR(200),
    DAST_TARGET_URL                     TEXT,
    DAST_PRESENCE_ID                    VARCHAR(100),
    -- SonarQube
    SONAR_PROJECT_NAME                  VARCHAR(200),
    SONAR_PROJECT_KEY                   VARCHAR(400),
    SONAR_INSTALLATION_NAME             VARCHAR(200),
    SONAR_CREDENTIALS_ID                VARCHAR(200),
    SONAR_AUTH_TOKEN_CREDENTIALS_ID     VARCHAR(200),
    SONAR_BADGE_TOKEN                   VARCHAR(200),
    SONAR_ADD_BADGES                    INT             DEFAULT 0 NOT NULL,
    SONAR_FULL_BADGES                   INT             DEFAULT 0 NOT NULL,
    SONAR_TASKS                         TEXT,
    SONAR_FLAGS                         TEXT,
    SONAR_DIRECTORY                     TEXT,
    SONAR_MAVEN_HOME                    TEXT,
    SONAR_ENVIRONMENT                   TEXT,
    -- Nexus IQ and SCA
    NEXUS_IQ_APPLICATION                VARCHAR(200),
    NEXUS_IQ_SCAN_PATTERNS              TEXT,
    NEXUS_IQ_STAGE                      VARCHAR(50)     DEFAULT 'build' NOT NULL,
    NEXUS_IQ_FAIL_ON_NETWORK_ERROR      INT             DEFAULT 0 NOT NULL,
    SCA_SCAN_NAME                       VARCHAR(200),
    -- Bitbucket
    REPOSITORY_URL                      TEXT,
    BITBUCKET_CREDENTIALS_ID            VARCHAR(200),
    BITBUCKET_AUTH_TYPE                 VARCHAR(20)     DEFAULT 'BASIC' NOT NULL,
    BITBUCKET_TYPE                      VARCHAR(20),
    BITBUCKET_TARGET_BRANCH             VARCHAR(200),
    BITBUCKET_CLONE_URL                 TEXT,
    BITBUCKET_REVIEWERS                 TEXT,
    -- GoldenFix; a null value inherits the global setting
    GOLDEN_FIX_ENABLED                  INT             DEFAULT 1 NOT NULL,
    GOLDEN_FIX_DIRECT_ONLY              INT,
    GOLDEN_FIX_MIN_THREAT_LEVEL         INT,
    GOLDEN_FIX_ECOSYSTEMS               VARCHAR(200),
    GOLDEN_FIX_VERSION_TYPES            TEXT,
    GOLDEN_FIX_EXCLUDE_DIRS             TEXT,
    GOLDEN_FIX_VERIFY                   INT,
    GOLDEN_FIX_VERIFY_ATTEMPTS          INT,
    GOLDEN_FIX_VERIFY_TIMEOUT           INT,
    GOLDEN_FIX_VERIFY_MAVEN             TEXT,
    GOLDEN_FIX_VERIFY_GRADLE            TEXT,
    GOLDEN_FIX_VERIFY_NPM               TEXT,
    GOLDEN_FIX_VERIFY_PIP               TEXT,
    GOLDEN_FIX_VERIFY_PUB               TEXT,
    GOLDEN_FIX_AUTHOR_NAME              VARCHAR(200),
    GOLDEN_FIX_AUTHOR_EMAIL             TEXT,
    GOLDEN_FIX_TIME_ZONE                VARCHAR(100),
    -- metrics in InfluxDB
    METRICS_ENABLED                     INT             DEFAULT 1 NOT NULL,
    INFLUX_PROJECT                      VARCHAR(200)    NOT NULL,
    INFLUX_ENV                          VARCHAR(50)     DEFAULT 'test' NOT NULL,
    -- Flutter
    FLUTTER_PLATFORM                    VARCHAR(20),
    FLUTTER_MODULES                     TEXT,
    FLUTTER_TEST_MODULES                TEXT,
    FLUTTER_TEST_SUBMODULES             TEXT,
    FLUTTER_TEST_SUBPLUGINS             TEXT,
    FLUTTER_SIGNING_CREDENTIALS_ID      VARCHAR(200),
    FLUTTER_PROD_LICENSE_CREDENTIALS_ID VARCHAR(200),
    FLUTTER_TEST_LICENSE_CREDENTIALS_ID VARCHAR(200),
    FLUTTER_DELIVERY_GROUP              VARCHAR(200),
    FLUTTER_DELIVERY_ARTIFACT           VARCHAR(200),
    FLUTTER_DELIVERY_PLUGIN             TEXT,
    FLUTTER_SONAR_SOURCES               TEXT,
    FLUTTER_SONAR_TESTS                 TEXT,
    FLUTTER_SONAR_PLUGIN                INT             DEFAULT 0 NOT NULL,
    FLUTTER_DART_ANALYZE_COMMAND        TEXT,
    FLUTTER_SONAR_SCANNER_VERSION       VARCHAR(50),
    CREATED_AT                          DATETIME(6)     NOT NULL,
    UPDATED_AT                          DATETIME(6)     NOT NULL,
    VERSION                             BIGINT          DEFAULT 0 NOT NULL,
    CONSTRAINT PK_DSO_SERVICE PRIMARY KEY (ID),
    CONSTRAINT FK_DSO_SERVICE_PRODUCT FOREIGN KEY (PRODUCT_ID) REFERENCES DSO_PRODUCT (ID) ON DELETE CASCADE,
    CONSTRAINT UK_DSO_SERVICE_NAME UNIQUE (PRODUCT_ID, NAME),
    CONSTRAINT UK_DSO_SERVICE_INFLUX UNIQUE (INFLUX_PROJECT, INFLUX_ENV),
    CONSTRAINT UK_DSO_SERVICE_SONAR_KEY UNIQUE (SONAR_PROJECT_KEY),
    CONSTRAINT CK_DSO_SERVICE_BUILD_TOOL CHECK (BUILD_TOOL IN ('GRADLE', 'MAVEN', 'FLUTTER')),
    CONSTRAINT CK_DSO_SERVICE_DEPLOY_TARGET CHECK (DEPLOY_TARGET IN ('VM', 'OPENSHIFT')),
    CONSTRAINT CK_DSO_SERVICE_BB_AUTH CHECK (BITBUCKET_AUTH_TYPE IN ('BASIC', 'BEARER')),
    CONSTRAINT CK_DSO_SERVICE_BB_TYPE CHECK (BITBUCKET_TYPE IN ('SERVER', 'CLOUD')),
    CONSTRAINT CK_DSO_SERVICE_FLUTTER CHECK (
        FLUTTER_PLATFORM IN ('APK', 'APPBUNDLE', 'IOS', 'MACOS', 'LINUX', 'WINDOWS', 'WEB')),
    CONSTRAINT CK_DSO_SERVICE_FLAGS CHECK (BUILD_TOOL_AUTO_SETUP IN (0, 1) AND UNIT_TEST_ALLOW_EMPTY IN (0, 1)
        AND UCD_SKIP_WAIT IN (0, 1) AND UCD_DEPLOY_WITH_SNAPSHOT IN (0, 1) AND UCD_UPDATE_SNAPSHOT_COMPONENTS IN (0, 1)
        AND UCD_INCLUDE_ONLY_DEPLOY_VERSIONS IN (0, 1) AND UCD_DEPLOY_ONLY_CHANGED IN (0, 1)
        AND APPSCAN_COMPILE IN (0, 1) AND APPSCAN_SOURCE_CODE_ONLY IN (0, 1) AND APPSCAN_USE_CONFIG_FILE IN (0, 1)
        AND APPSCAN_INSECURE_TLS IN (0, 1) AND DAST_ENABLED IN (0, 1) AND SONAR_ADD_BADGES IN (0, 1)
        AND SONAR_FULL_BADGES IN (0, 1) AND NEXUS_IQ_FAIL_ON_NETWORK_ERROR IN (0, 1) AND GOLDEN_FIX_ENABLED IN (0, 1)
        AND GOLDEN_FIX_DIRECT_ONLY IN (0, 1) AND GOLDEN_FIX_VERIFY IN (0, 1) AND METRICS_ENABLED IN (0, 1)
        AND FLUTTER_SONAR_PLUGIN IN (0, 1)),
    CONSTRAINT CK_DSO_SERVICE_DAST CHECK (DAST_ENABLED = 0 OR DAST_TARGET_URL IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
--rollback DROP TABLE DSO_SERVICE;

--changeset dso-portal:001-service-test-job dbms:mysql
CREATE TABLE DSO_SERVICE_TEST_JOB (
    SERVICE_ID                  BIGINT          NOT NULL,
    POSITION                    INT             NOT NULL,
    STAGE                       VARCHAR(20)     NOT NULL,
    NAME                        VARCHAR(200),
    JOB_TYPE                    VARCHAR(20),
    JOB                         TEXT            NOT NULL,
    TIMEOUT_MINUTES             INT,
    PARAMETERS                  TEXT,
    REMOTE_JENKINS              VARCHAR(200),
    REMOTE_JENKINS_URL          TEXT,
    CREDENTIALS_ID              VARCHAR(200),
    CONSTRAINT PK_DSO_SERVICE_TEST_JOB PRIMARY KEY (SERVICE_ID, POSITION),
    CONSTRAINT FK_DSO_TEST_JOB_SERVICE FOREIGN KEY (SERVICE_ID) REFERENCES DSO_SERVICE (ID) ON DELETE CASCADE,
    CONSTRAINT CK_DSO_TEST_JOB_STAGE CHECK (STAGE IN ('SMOKE', 'REGRESSION', 'PERFORMANCE')),
    CONSTRAINT CK_DSO_TEST_JOB_TYPE CHECK (JOB_TYPE IN ('LOCAL', 'REMOTE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
--rollback DROP TABLE DSO_SERVICE_TEST_JOB;

--changeset dso-portal:001-service-ssh-target dbms:mysql
CREATE TABLE DSO_SERVICE_SSH_TARGET (
    SERVICE_ID                  BIGINT          NOT NULL,
    REGION                      VARCHAR(10)     NOT NULL,
    HOST                        TEXT,
    SSH_USER                    VARCHAR(100),
    DEPLOY_DIR                  TEXT,
    DEPLOY_SCRIPT               TEXT,
    VERSION_FILE                TEXT,
    CONSTRAINT PK_DSO_SERVICE_SSH_TARGET PRIMARY KEY (SERVICE_ID, REGION),
    CONSTRAINT FK_DSO_SSH_TARGET_SERVICE FOREIGN KEY (SERVICE_ID) REFERENCES DSO_SERVICE (ID) ON DELETE CASCADE,
    CONSTRAINT CK_DSO_SSH_TARGET_REGION CHECK (REGION IN ('RD', 'QC'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
--rollback DROP TABLE DSO_SERVICE_SSH_TARGET;

--changeset dso-portal:001-service-openshift-target dbms:mysql
CREATE TABLE DSO_SERVICE_OPENSHIFT_TARGET (
    SERVICE_ID                      BIGINT          NOT NULL,
    REGION                          VARCHAR(10)     NOT NULL,
    PROJECT_BUILD                   VARCHAR(200),
    BUILD_CONFIG_PATH               TEXT,
    DOCKER_FILE_PATH                TEXT,
    BUILD_CONTEXT                   TEXT,
    ADD_FILE                        TEXT,
    DOCKER_REPO_PUSH                TEXT,
    DOCKER_REPO_PULL                TEXT,
    CERT_DIR                        TEXT,
    NEXUS_AUTH_FILE                 TEXT,
    PROJECT_DEPLOYMENT              VARCHAR(200),
    DEPLOY_CONFIG_PATH              TEXT,
    CONFIG_PATH                     TEXT,
    SKIP_CONFIG_DEPLOY              INT             DEFAULT 0 NOT NULL,
    HEALTH_CHECK_URL                TEXT,
    ROUTE_HOSTNAME                  TEXT,
    DEPLOYMENT_PATH                 TEXT,
    DEPLOYMENT_REPO_URL             TEXT,
    DEPLOYMENT_REPO_BRANCH          VARCHAR(200),
    DEPLOYMENT_REPO_CREDENTIALS_ID  VARCHAR(200),
    CONSTRAINT PK_DSO_SERVICE_OS_TARGET PRIMARY KEY (SERVICE_ID, REGION),
    CONSTRAINT FK_DSO_OS_TARGET_SERVICE FOREIGN KEY (SERVICE_ID) REFERENCES DSO_SERVICE (ID) ON DELETE CASCADE,
    CONSTRAINT CK_DSO_OS_TARGET_REGION CHECK (REGION IN ('RD', 'QC')),
    CONSTRAINT CK_DSO_OS_TARGET_FLAGS CHECK (SKIP_CONFIG_DEPLOY IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
--rollback DROP TABLE DSO_SERVICE_OPENSHIFT_TARGET;

-- POSITION is not unique: when a service's applications are replaced, Hibernate inserts the new rows before it
-- deletes the old ones.
--changeset dso-portal:001-ucd-application dbms:mysql
CREATE TABLE DSO_UCD_APPLICATION (
    ID                          BIGINT          NOT NULL AUTO_INCREMENT,
    SERVICE_ID                  BIGINT          NOT NULL,
    POSITION                    INT             NOT NULL,
    APPLICATION_NAME            VARCHAR(200)    NOT NULL,
    DEPLOY_ORDER                INT,
    ENVIRONMENTS                TEXT,
    SNAPSHOT_NAME               VARCHAR(200),
    CONSTRAINT PK_DSO_UCD_APPLICATION PRIMARY KEY (ID),
    CONSTRAINT FK_DSO_UCD_APP_SERVICE FOREIGN KEY (SERVICE_ID) REFERENCES DSO_SERVICE (ID) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX IX_DSO_UCD_APPLICATION_SERVICE ON DSO_UCD_APPLICATION (SERVICE_ID, POSITION);
--rollback DROP TABLE DSO_UCD_APPLICATION;

--changeset dso-portal:001-ucd-component dbms:mysql
CREATE TABLE DSO_UCD_COMPONENT (
    APPLICATION_ID              BIGINT          NOT NULL,
    POSITION                    INT             NOT NULL,
    COMPONENT_NAME              VARCHAR(200)    NOT NULL,
    BASE_DIR                    TEXT,
    FILE_INCLUDE_PATTERNS       TEXT,
    FILE_EXCLUDE_PATTERNS       TEXT,
    VERSION_PREFIX              VARCHAR(200),
    COMPONENT_VERSION           VARCHAR(200),
    INCREMENTAL_VERSION         INT             DEFAULT 0 NOT NULL,
    CONSTRAINT PK_DSO_UCD_COMPONENT PRIMARY KEY (APPLICATION_ID, POSITION),
    CONSTRAINT FK_DSO_UCD_COMP_APPLICATION FOREIGN KEY (APPLICATION_ID)
        REFERENCES DSO_UCD_APPLICATION (ID) ON DELETE CASCADE,
    CONSTRAINT CK_DSO_UCD_COMP_FLAGS CHECK (INCREMENTAL_VERSION IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
--rollback DROP TABLE DSO_UCD_COMPONENT;
