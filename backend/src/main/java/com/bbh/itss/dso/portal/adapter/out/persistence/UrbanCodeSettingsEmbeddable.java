package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeSettings;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import org.hibernate.type.NumericBooleanConverter;

@Embeddable
public record UrbanCodeSettingsEmbeddable(
        @Column(name = "UCD_SITE_NAME", length = 200) String siteName,
        @Column(name = "UCD_DEPLOY_PROCESS", length = 200) String deployProcess,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "UCD_SKIP_WAIT", nullable = false) Boolean skipWait,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "UCD_DEPLOY_WITH_SNAPSHOT", nullable = false) Boolean deployWithSnapshot,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "UCD_UPDATE_SNAPSHOT_COMPONENTS", nullable = false) Boolean updateSnapshotComponents,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "UCD_INCLUDE_ONLY_DEPLOY_VERSIONS", nullable = false) Boolean includeOnlyDeployVersions,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "UCD_DEPLOY_ONLY_CHANGED", nullable = false) Boolean deployOnlyChanged,
        @Column(name = "UCD_DEPLOY_DESCRIPTION", length = 1000) String deployDescription,
        @Column(name = "UCD_REQUEST_PROPERTIES", length = 2000) String requestProperties) {

    static UrbanCodeSettingsEmbeddable of(UrbanCodeSettings urbanCode) {
        return new UrbanCodeSettingsEmbeddable(urbanCode.siteName(), urbanCode.deployProcess(), urbanCode.skipWait(),
                urbanCode.deployWithSnapshot(), urbanCode.updateSnapshotComponents(),
                urbanCode.includeOnlyDeployVersions(), urbanCode.deployOnlyChanged(), urbanCode.deployDescription(),
                urbanCode.requestProperties());
    }

    UrbanCodeSettings toDomain() {
        return new UrbanCodeSettings(siteName, deployProcess, skipWait, deployWithSnapshot, updateSnapshotComponents,
                includeOnlyDeployVersions, deployOnlyChanged, deployDescription, requestProperties);
    }
}
