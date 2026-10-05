package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;

import java.util.List;

public record UrbanCodeSettings(String siteName, String deployProcess, Boolean skipWait, Boolean deployWithSnapshot,
                                Boolean updateSnapshotComponents, Boolean includeOnlyDeployVersions,
                                Boolean deployOnlyChanged, String deployDescription, String requestProperties) {

    public static final UrbanCodeSettings DEFAULTS = new UrbanCodeSettings(null, null, false, true, false, true, false,
            null, null);

    public UrbanCodeSettings {
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

    public void writeTo(ConfigTree config, List<UrbanCodeApplicationSettings> applications) {
        if (applications.isEmpty()) {
            return;
        }
        String path = "deploy.vm.dod.";
        config.set(path + "siteName", siteName)
                .set(path + "deployProcess", deployProcess)
                .set(path + "skipWait", skipWait)
                .set(path + "deployWithSnapshot", deployWithSnapshot)
                .set(path + "updateSnapshotComp", updateSnapshotComponents)
                .set(path + "includeOnlyDeployVersions", includeOnlyDeployVersions)
                .set(path + "deployOnlyChanged", deployOnlyChanged)
                .set(path + "deployDescription", deployDescription)
                .set(path + "requestProperties", requestProperties)
                .set(path + "applications", applications.stream().map(UrbanCodeApplicationSettings::toConfig).toList());
    }
}
