package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public record UrbanCodeApplicationSettings(
        String applicationName,
        Integer order,
        List<String> environments,
        String snapshotName,
        List<UrbanCodeComponent> components) {

    public UrbanCodeApplicationSettings {
        applicationName = applicationName == null ? null : applicationName.trim();
        environments = Text.clean(environments);
        snapshotName = Text.trimToNull(snapshotName);
        components = components == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(components));
    }

    public Map<String, Object> toConfig() {
        return new ConfigTree()
                .set("applicationName", applicationName)
                .set("order", order)
                .set("environments", environments)
                .set("snapshotName", snapshotName)
                .set("components", components.stream().map(UrbanCodeComponent::toConfig).toList())
                .toMap();
    }
}
