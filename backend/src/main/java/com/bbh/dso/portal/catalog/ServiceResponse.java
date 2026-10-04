package com.bbh.dso.portal.catalog;

import java.util.List;

public record ServiceResponse(
        Long id,
        String name,
        String description,
        BuildTool buildTool,
        DeployTarget deployTarget,
        String sourceDir,
        String javaPath,
        boolean buildToolAutoSetup,
        String appScanAppId,
        String sastScanName,
        boolean dastEnabled,
        String dastTargetUrl,
        String dastPresenceId,
        String sonarProjectName,
        String sonarProjectKey,
        String nexusIqApplication,
        List<String> nexusIqScanPatterns,
        String repositoryUrl,
        String bitbucketCredentialsId,
        boolean goldenFixEnabled,
        boolean metricsEnabled,
        String influxProject,
        String influxEnv,
        String appName,
        String artifactName,
        String additionalConfig) {

    static ServiceResponse from(ServiceDefinition s) {
        return new ServiceResponse(s.getId(), s.getName(), s.getDescription(), s.getBuildTool(), s.getDeployTarget(),
                s.getSourceDir(), s.getJavaPath(), s.isBuildToolAutoSetup(), s.getAppScanAppId(), s.getSastScanName(),
                s.isDastEnabled(), s.getDastTargetUrl(), s.getDastPresenceId(), s.getSonarProjectName(),
                s.getSonarProjectKey(), s.getNexusIqApplication(), ScanPatterns.split(s.getNexusIqScanPatterns()),
                s.getRepositoryUrl(), s.getBitbucketCredentialsId(), s.isGoldenFixEnabled(), s.isMetricsEnabled(),
                s.getInfluxProject(), s.getInfluxEnv(), s.getAppName(), s.getArtifactName(), s.getAdditionalConfig());
    }
}
