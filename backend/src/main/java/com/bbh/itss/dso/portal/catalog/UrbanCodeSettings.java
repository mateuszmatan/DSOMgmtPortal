package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Size;
import org.hibernate.type.NumericBooleanConverter;

import java.util.List;

@Embeddable
public record UrbanCodeSettings(
        @Size(max = 200)
        @Column(name = "UCD_SITE_NAME", length = 200)
        String siteName,
        @Size(max = 200)
        @Column(name = "UCD_DEPLOY_PROCESS", length = 200)
        String deployProcess,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "UCD_SKIP_WAIT", nullable = false)
        Boolean skipWait,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "UCD_DEPLOY_WITH_SNAPSHOT", nullable = false)
        Boolean deployWithSnapshot,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "UCD_UPDATE_SNAPSHOT_COMPONENTS", nullable = false)
        Boolean updateSnapshotComponents,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "UCD_INCLUDE_ONLY_DEPLOY_VERSIONS", nullable = false)
        Boolean includeOnlyDeployVersions,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "UCD_DEPLOY_ONLY_CHANGED", nullable = false)
        Boolean deployOnlyChanged,
        @Size(max = 1000)
        @Column(name = "UCD_DEPLOY_DESCRIPTION", length = 1000)
        String deployDescription,
        @Size(max = 2000)
        @Column(name = "UCD_REQUEST_PROPERTIES", length = 2000)
        String requestProperties) {

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
