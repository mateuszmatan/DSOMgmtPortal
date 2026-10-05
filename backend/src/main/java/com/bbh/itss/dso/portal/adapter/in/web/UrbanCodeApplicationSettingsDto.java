package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.UrbanCodeApplicationSettings;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record UrbanCodeApplicationSettingsDto(
        @NotBlank @Size(max = 200) String applicationName,
        @Min(1) @Max(999) Integer order,
        @Size(max = 20) List<@Pattern(regexp = "^[A-Za-z0-9_-]{1,20}$",
                message = "environment names may contain letters, digits, '-' and '_'") String> environments,
        @Size(max = 200) String snapshotName,
        @NotNull @Size(max = 50) List<@NotNull @Valid UrbanCodeComponentDto> components) {

    public UrbanCodeApplicationSettingsDto {
        applicationName = applicationName == null ? null : applicationName.trim();
        environments = Text.clean(environments);
        snapshotName = Text.trimToNull(snapshotName);
        components = components == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(components));
    }

    static UrbanCodeApplicationSettingsDto from(UrbanCodeApplicationSettings source) {
        return new UrbanCodeApplicationSettingsDto(source.applicationName(), source.order(), source.environments(),
                source.snapshotName(), source.components().stream().map(UrbanCodeComponentDto::from).toList());
    }

    UrbanCodeApplicationSettings toDomain() {
        return new UrbanCodeApplicationSettings(applicationName, order, environments, snapshotName,
                components.stream().map(UrbanCodeComponentDto::toDomain).toList());
    }
}
