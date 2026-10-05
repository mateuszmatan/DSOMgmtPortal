package com.bbh.itss.dso.portal.domain.settings;

import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;

public record ServiceDefaults(BuildTool buildTool, DeployTarget deployTarget, String sourceDir, Integer testsMaxParallel) {

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
