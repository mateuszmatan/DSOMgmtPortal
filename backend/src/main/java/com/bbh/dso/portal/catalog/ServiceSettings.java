package com.bbh.dso.portal.catalog;

import com.bbh.dso.portal.common.ValidationProblems;

import java.util.List;

/**
 * Every setting of a service, grouped as the sections of its config.yaml entry.
 */
public record ServiceSettings(
        BuildSettings build,
        DeploymentSettings deployment,
        AppScanSettings appScan,
        SonarSettings sonar,
        NexusIqSettings nexusIq,
        ScmSettings scm,
        MetricsSettings metrics,
        AdditionalConfig additionalConfig) {

    /** Optional sections left out are replaced by their empty defaults. */
    public ServiceSettings {
        sonar = sonar == null ? SonarSettings.NONE : sonar;
        nexusIq = nexusIq == null ? NexusIqSettings.NONE : nexusIq;
        scm = scm == null ? ScmSettings.NONE : scm;
        metrics = metrics == null ? MetricsSettings.DEFAULTS : metrics;
        additionalConfig = additionalConfig == null ? AdditionalConfig.NONE : additionalConfig;
    }

    /**
     * The sections in the order they are written: the additional YAML first, so every dedicated field
     * overrides what the YAML says about the same key.
     */
    public List<ConfigSection> sections() {
        return List.of(additionalConfig, build, deployment, appScan, sonar, nexusIq, scm, metrics);
    }

    public void validate(ValidationProblems problems) {
        build.validate(problems.at("build"));
        deployment.validate(problems.at("deployment"));
        appScan.validate(problems.at("appScan"));
        sonar.validate(problems.at("sonar"));
        nexusIq.validate(problems.at("nexusIq"));
        scm.validate(problems.at("scm"));
        metrics.validate(problems.at("metrics"));
        additionalConfig.validate(problems.at("additionalConfig"));
    }
}
