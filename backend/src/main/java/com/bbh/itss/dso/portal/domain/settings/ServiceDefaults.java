package com.bbh.itss.dso.portal.domain.settings;

import com.bbh.itss.dso.portal.domain.catalog.BuildSettings;
import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.shared.ConfigTree;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trim;

public record ServiceDefaults(BuildTool buildTool, DeployTarget deployTarget, String sourceDir, Integer testsMaxParallel) {

    public ServiceDefaults {
        sourceDir = defaultIfBlank(trim(sourceDir), BuildSettings.DEFAULT_SOURCE_DIR);
    }

    public void writeTo(ConfigTree defaults) {
        defaults.set("buildTool", buildTool)
                .set("deployTarget", deployTarget)
                .set("sourceDir", sourceDir)
                .set("tests.maxParallel", testsMaxParallel);
    }
}
