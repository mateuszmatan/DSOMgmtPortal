package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.adapter.out.persistence.ServiceEntity.GoldenFixPolicyEmbeddable;
import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettings;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues;
import com.bbh.itss.dso.portal.domain.settings.Scanner;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import org.hibernate.type.NumericBooleanConverter;
import org.springframework.data.domain.Persistable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "DSO_GLOBAL_SETTINGS")
public class GlobalSettingsEntity extends AuditedEntity implements Persistable<Long> {

    static final long ID = 1L;

    @Id
    @Column(name = "ID")
    private Long id = ID;

    @Embedded
    private ValuesEmbeddable values;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "DSO_GLOBAL_SEVERITY_LIMIT", joinColumns = @JoinColumn(name = "SETTINGS_ID"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "SCANNER", length = 20)
    private Map<Scanner, SeverityLimitsEmbeddable> limits = new HashMap<>();

    protected GlobalSettingsEntity() {
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return getCreatedAt() == null;
    }

    GlobalSettings toDomain() {
        return new GlobalSettings(RecordMapper.map(GlobalSettingsValues.class, values, this), getVersion(),
                getUpdatedAt());
    }

    void apply(GlobalSettingsValues source) {
        values = RecordMapper.map(source, ValuesEmbeddable.class);
        Map<Scanner, SeverityLimitsEmbeddable> replacement = new EnumMap<>(Scanner.class);
        source.limits().forEach((scanner, limit) -> replacement.put(scanner,
                RecordMapper.map(limit, SeverityLimitsEmbeddable.class)));
        if (!limits.equals(replacement)) {
            limits.clear();
            limits.putAll(replacement);
        }
    }

    @Embeddable
    public record ValuesEmbeddable(
            @Embedded PlatformSettingsEmbeddable platform,
            @Embedded DeploymentDefaultsEmbeddable deployment,
            @Embedded ScanSettingsEmbeddable scans,
            @Embedded ReleaseGateSettingsEmbeddable releaseGate,
            @Embedded ServiceDefaultsEmbeddable serviceDefaults,
            @Embedded GoldenFixPolicyEmbeddable goldenFix) {
    }

    @Embeddable
    public record PlatformSettingsEmbeddable(
            @Column(name = "JENKINS_URL", length = 500) String jenkinsUrl,
            @Column(name = "JENKINS_LIBRARY", nullable = false, length = 200) String jenkinsLibrary,
            @Column(name = "ASOC_URL", nullable = false, length = 500) String asocUrl,
            @Column(name = "APPSCAN_CLIENT_LINUX_URL", nullable = false, length = 1000) String appScanClientLinuxUrl,
            @Column(name = "APPSCAN_CLIENT_WINDOWS_URL", nullable = false, length = 1000) String appScanClientWindowsUrl,
            @Column(name = "PROXY_HOST", length = 255) String proxyHost,
            @Column(name = "PROXY_PORT") Integer proxyPort,
            @Column(name = "PROXY_USER", length = 100) String proxyUser,
            @Column(name = "OIS_HOST", length = 255) String oisHost,
            @Column(name = "SONAR_SERVER_URL", nullable = false, length = 500) String sonarServerUrl,
            @Column(name = "SONAR_INSTALLATION_NAME", nullable = false, length = 200) String sonarInstallationName,
            @Column(name = "NEXUS_IQ_SERVER_URL", nullable = false, length = 500) String nexusIqServerUrl,
            @Column(name = "NEXUS_IQ_CREDENTIALS_ID", nullable = false, length = 200) String nexusIqCredentialsId,
            @Column(name = "NEXUS_SNAPSHOT_URL", length = 1000) String nexusSnapshotRepositoryUrl,
            @Column(name = "NEXUS_SNAPSHOT_REPOSITORY_ID", length = 200) String nexusSnapshotRepositoryId,
            @Column(name = "INFLUX_WRITE_URL", length = 1000) String influxWriteUrl,
            @Column(name = "INFLUX_CREDENTIALS_ID", length = 200) String influxCredentialsId,
            @Column(name = "IOS_BUILD_AGENT", length = 255) String iosBuildAgent) {
    }

    @Embeddable
    public record DeploymentDefaultsEmbeddable(
            @Column(name = "UCD_SITE_NAME", nullable = false, length = 200) String urbanCodeSiteName,
            @Column(name = "UCD_DEPLOY_PROCESS", nullable = false, length = 200) String urbanCodeDeployProcess,
            @Column(name = "RD_HOST", nullable = false, length = 255) String rdHost,
            @Column(name = "QC_HOST", nullable = false, length = 255) String qcHost,
            @Column(name = "SSH_USER", nullable = false, length = 100) String sshUser,
            @Column(name = "DEPLOY_SCRIPT", nullable = false, length = 500) String deployScript,
            @Column(name = "VERSION_FILE", nullable = false, length = 500) String versionFile) {
    }

    @Embeddable
    public record SeverityLimitsEmbeddable(
            @Column(name = "MAX_CRITICAL", nullable = false) Integer maxCritical,
            @Column(name = "MAX_HIGH", nullable = false) Integer maxHigh,
            @Column(name = "MAX_MEDIUM", nullable = false) Integer maxMedium) {
    }

    @Embeddable
    public record ScanSettingsEmbeddable(
            @Column(name = "COVERAGE_MIN_LINE", nullable = false) Integer coverageMinLine,
            @Column(name = "SAST_PREPARE_TIMEOUT_MIN", nullable = false) Integer sastPrepareTimeoutMinutes,
            @Column(name = "SAST_POLL_TIMEOUT_MIN", nullable = false) Integer sastPollTimeoutMinutes,
            @Column(name = "SAST_POLL_INTERVAL_SEC", nullable = false) Integer sastPollIntervalSeconds,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "SCA_ENABLED", nullable = false) Boolean scaEnabled,
            @Column(name = "SCA_POLL_TIMEOUT_MIN", nullable = false) Integer scaPollTimeoutMinutes,
            @Column(name = "SCA_POLL_INTERVAL_SEC", nullable = false) Integer scaPollIntervalSeconds,
            @Column(name = "DAST_POLL_TIMEOUT_MIN", nullable = false) Integer dastPollTimeoutMinutes,
            @Column(name = "DAST_POLL_INTERVAL_SEC", nullable = false) Integer dastPollIntervalSeconds,
            @Column(name = "DAST_REPORT_TIMEOUT_MIN", nullable = false) Integer dastReportTimeoutMinutes,
            @Column(name = "DAST_REPORT_INTERVAL_SEC", nullable = false) Integer dastReportIntervalSeconds,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "SONAR_WAIT_FOR_QUALITY_GATE", nullable = false) Boolean sonarWaitForQualityGate,
            @Column(name = "SONAR_QUALITY_GATE_TIMEOUT_MIN", nullable = false) Integer sonarQualityGateTimeoutMinutes) {
    }

    @Embeddable
    public record ReleaseGateSettingsEmbeddable(
            @Convert(converter = ScannerListConverter.class)
            @Column(name = "RELEASE_GATE_SCANNERS", length = 100) List<Scanner> scanners,
            @Convert(converter = NumericBooleanConverter.class)
            @Column(name = "RELEASE_GATE_REQUIRE_COVERAGE", nullable = false) Boolean requireCoverage,
            @Column(name = "RELEASE_GATE_STATE_FILE", nullable = false, length = 200) String stateFile) {
    }

    @Embeddable
    public record ServiceDefaultsEmbeddable(
            @Enumerated(EnumType.STRING)
            @Column(name = "DEFAULT_BUILD_TOOL", nullable = false, length = 20) BuildTool buildTool,
            @Enumerated(EnumType.STRING)
            @Column(name = "DEFAULT_DEPLOY_TARGET", nullable = false, length = 20) DeployTarget deployTarget,
            @Column(name = "DEFAULT_SOURCE_DIR", nullable = false, length = 500) String sourceDir,
            @Column(name = "TESTS_MAX_PARALLEL", nullable = false) Integer testsMaxParallel) {
    }
}
