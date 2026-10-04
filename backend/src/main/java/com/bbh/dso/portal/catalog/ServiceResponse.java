package com.bbh.dso.portal.catalog;

public record ServiceResponse(
        Long id,
        String name,
        String description,
        BuildSettings build,
        DeploymentSettings deployment,
        AppScanSettings appScan,
        SonarSettings sonar,
        NexusIqSettings nexusIq,
        ScmSettings scm,
        MetricsSettings metrics,
        AdditionalConfig additionalConfig) {

    static ServiceResponse from(ServiceDefinition service) {
        ServiceSettings s = service.settings();
        return new ServiceResponse(service.getId(), service.getName(), service.getDescription(), s.build(),
                s.deployment(), s.appScan(), s.sonar(), s.nexusIq(), s.scm(), s.metrics(), s.additionalConfig());
    }
}
