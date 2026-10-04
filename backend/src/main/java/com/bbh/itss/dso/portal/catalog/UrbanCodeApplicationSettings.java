package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.DelimitedListConverter;
import com.bbh.itss.dso.portal.common.Text;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * An UrbanCode Deploy application the full pipeline deploys, with the components it publishes first, an entry
 * of {@code deploy.vm.dod.applications}.
 *
 * @param order        deployment order among the service's applications, optional
 * @param environments the environments the application is deployed to; empty means all of them
 */
public record UrbanCodeApplicationSettings(
        @NotBlank @Size(max = 200) String applicationName,
        @Min(1) @Max(999) Integer order,
        @Size(max = 20) List<@Pattern(regexp = "^[A-Za-z0-9_-]{1,20}$",
                message = "environment names may contain letters, digits, '-' and '_'") String> environments,
        @Size(max = 200) String snapshotName,
        @NotNull @Size(max = 50) List<@NotNull @Valid UrbanCodeComponent> components) {

    public UrbanCodeApplicationSettings {
        applicationName = applicationName == null ? null : applicationName.trim();
        environments = DelimitedListConverter.clean(environments);
        snapshotName = Text.trimToNull(snapshotName);
        components = components == null ? List.of() : List.copyOf(components);
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
