package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.trimToNull;

public record UrbanCodeApplicationSettings(String applicationName, Integer order, List<String> environments,
                                           String snapshotName, String siteName, String deployProcess,
                                           Boolean skipWait, Boolean deployWithSnapshot,
                                           Boolean updateSnapshotComponents, Boolean includeOnlyDeployVersions,
                                           Boolean deployOnlyChanged, String deployDescription, String description,
                                           String requestProperties, List<UrbanCodeComponent> components) {

    public UrbanCodeApplicationSettings {
        applicationName = applicationName == null ? null : applicationName.trim();
        environments = Text.clean(environments);
        snapshotName = trimToNull(snapshotName);
        siteName = trimToNull(siteName);
        deployProcess = trimToNull(deployProcess);
        deployDescription = trimToNull(deployDescription);
        description = trimToNull(description);
        requestProperties = trimToNull(requestProperties);
        components = components == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(components));
    }

    public static UrbanCodeApplicationSettings of(String applicationName, Integer order, List<String> environments,
                                                  String snapshotName, List<UrbanCodeComponent> components) {
        return new UrbanCodeApplicationSettings(applicationName, order, environments, snapshotName, null, null, null,
                null, null, null, null, null, null, null, components);
    }

    public void validate(ValidationProblems problems) {
        problems.require("components", components,
                "add at least one component: the deployment uploads the components of the application");
        for (int i = 0; i < components.size(); i++) {
            components.get(i).validate(problems.at("components[" + i + "]"));
        }
    }

    public Map<String, Object> toConfig() {
        return new ConfigTree()
                .set("applicationName", applicationName)
                .set("order", order)
                .set("environments", environments)
                .set("snapshotName", snapshotName)
                .set("siteName", siteName)
                .set("deployProcess", deployProcess)
                .set("skipWait", skipWait)
                .set("deployWithSnapshot", deployWithSnapshot)
                .set("updateSnapshotComp", updateSnapshotComponents)
                .set("includeOnlyDeployVersions", includeOnlyDeployVersions)
                .set("deployOnlyChanged", deployOnlyChanged)
                .set("deployDescription", deployDescription)
                .set("description", description)
                .set("requestProperties", requestProperties)
                .set("components", components.stream().map(UrbanCodeComponent::toConfig).toList())
                .toMap();
    }
}
