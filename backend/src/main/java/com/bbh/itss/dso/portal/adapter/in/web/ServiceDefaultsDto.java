package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.settings.ServiceDefaults;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ServiceDefaultsDto(
        @NotNull BuildTool buildTool,
        @NotNull DeployTarget deployTarget,
        @Size(max = 500) String sourceDir,
        @NotNull @Min(1) @Max(100) Integer testsMaxParallel) {

    public ServiceDefaultsDto {
        sourceDir = Text.orDefault(sourceDir, ".");
    }

    static ServiceDefaultsDto from(ServiceDefaults defaults) {
        return new ServiceDefaultsDto(defaults.buildTool(), defaults.deployTarget(), defaults.sourceDir(),
                defaults.testsMaxParallel());
    }

    ServiceDefaults toDomain() {
        return new ServiceDefaults(buildTool, deployTarget, sourceDir, testsMaxParallel);
    }
}
