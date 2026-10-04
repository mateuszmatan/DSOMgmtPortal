package com.bbh.itss.dso.portal.settings;

import com.bbh.itss.dso.portal.catalog.BuildTool;
import com.bbh.itss.dso.portal.catalog.ConfigTree;
import com.bbh.itss.dso.portal.catalog.DeployTarget;
import com.bbh.itss.dso.portal.common.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Embeddable
public record ServiceDefaults(
        @NotNull
        @Enumerated(EnumType.STRING)
        @Column(name = "DEFAULT_BUILD_TOOL", nullable = false, length = 20)
        BuildTool buildTool,
        @NotNull
        @Enumerated(EnumType.STRING)
        @Column(name = "DEFAULT_DEPLOY_TARGET", nullable = false, length = 20)
        DeployTarget deployTarget,
        @Size(max = 500)
        @Column(name = "DEFAULT_SOURCE_DIR", nullable = false, length = 500)
        String sourceDir,
        @NotNull @Min(1) @Max(100)
        @Column(name = "TESTS_MAX_PARALLEL", nullable = false)
        Integer testsMaxParallel) {

    public ServiceDefaults {
        sourceDir = Text.orDefault(sourceDir, ".");
    }

    public void writeTo(ConfigTree defaults) {
        defaults.set("buildTool", buildTool.configValue())
                .set("deployTarget", deployTarget.configValue())
                .set("sourceDir", sourceDir)
                .set("tests.maxParallel", testsMaxParallel);
    }
}
