package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeSettings;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.validation.constraints.Size;

public record UrbanCodeSettingsDto(
        @Size(max = 200)
        String siteName,
        @Size(max = 200)
        String deployProcess,
        Boolean skipWait,
        Boolean deployWithSnapshot,
        Boolean updateSnapshotComponents,
        Boolean includeOnlyDeployVersions,
        Boolean deployOnlyChanged,
        @Size(max = 1000)
        String deployDescription,
        @Size(max = 2000)
        String requestProperties) {

    public UrbanCodeSettingsDto {
        siteName = Text.trimToNull(siteName);
        deployProcess = Text.trimToNull(deployProcess);
        skipWait = Boolean.TRUE.equals(skipWait);
        deployWithSnapshot = !Boolean.FALSE.equals(deployWithSnapshot);
        updateSnapshotComponents = Boolean.TRUE.equals(updateSnapshotComponents);
        includeOnlyDeployVersions = !Boolean.FALSE.equals(includeOnlyDeployVersions);
        deployOnlyChanged = Boolean.TRUE.equals(deployOnlyChanged);
        deployDescription = Text.trimToNull(deployDescription);
        requestProperties = Text.trimToNull(requestProperties);
    }

    static UrbanCodeSettingsDto from(UrbanCodeSettings source) {
        return new UrbanCodeSettingsDto(source.siteName(), source.deployProcess(), source.skipWait(),
                source.deployWithSnapshot(), source.updateSnapshotComponents(), source.includeOnlyDeployVersions(),
                source.deployOnlyChanged(), source.deployDescription(), source.requestProperties());
    }

    UrbanCodeSettings toDomain() {
        return new UrbanCodeSettings(siteName, deployProcess, skipWait, deployWithSnapshot, updateSnapshotComponents,
                includeOnlyDeployVersions, deployOnlyChanged, deployDescription, requestProperties);
    }
}
