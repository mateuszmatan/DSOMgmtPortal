package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.catalog.BuildTool;
import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.settings.ServiceDefaults;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public record ServiceDefaultsEmbeddable(
        @Enumerated(EnumType.STRING)
        @Column(name = "DEFAULT_BUILD_TOOL", nullable = false, length = 20) BuildTool buildTool,
        @Enumerated(EnumType.STRING)
        @Column(name = "DEFAULT_DEPLOY_TARGET", nullable = false, length = 20) DeployTarget deployTarget,
        @Column(name = "DEFAULT_SOURCE_DIR", nullable = false, length = 500) String sourceDir,
        @Column(name = "TESTS_MAX_PARALLEL", nullable = false) Integer testsMaxParallel) {

    static ServiceDefaultsEmbeddable of(ServiceDefaults defaults) {
        return new ServiceDefaultsEmbeddable(defaults.buildTool(), defaults.deployTarget(), defaults.sourceDir(),
                defaults.testsMaxParallel());
    }

    ServiceDefaults toDomain() {
        return new ServiceDefaults(buildTool, deployTarget, sourceDir, testsMaxParallel);
    }
}
