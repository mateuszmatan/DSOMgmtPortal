package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;

import java.util.Map;

public record OpenShiftTarget(
        String projectBuild,
        String buildConfigPath,
        String dockerFilePath,
        String buildContext,
        String addFile,
        String dockerRepoPush,
        String dockerRepoPull,
        String certDir,
        String nexusAuthFile,
        String projectDeployment,
        String deployConfigPath,
        String configPath,
        Boolean skipConfigDeploy,
        String healthCheckUrl,
        String routeHostname,
        String deploymentPath,
        String deploymentRepoUrl,
        String deploymentRepoBranch,
        String deploymentRepoCredentialsId) {

    public OpenShiftTarget {
        projectBuild = Text.trimToNull(projectBuild);
        buildConfigPath = Text.trimToNull(buildConfigPath);
        dockerFilePath = Text.trimToNull(dockerFilePath);
        buildContext = Text.trimToNull(buildContext);
        addFile = Text.trimToNull(addFile);
        dockerRepoPush = Text.trimToNull(dockerRepoPush);
        dockerRepoPull = Text.trimToNull(dockerRepoPull);
        certDir = Text.trimToNull(certDir);
        nexusAuthFile = Text.trimToNull(nexusAuthFile);
        projectDeployment = Text.trimToNull(projectDeployment);
        deployConfigPath = Text.trimToNull(deployConfigPath);
        configPath = Text.trimToNull(configPath);
        skipConfigDeploy = Boolean.TRUE.equals(skipConfigDeploy);
        healthCheckUrl = Text.trimToNull(healthCheckUrl);
        routeHostname = Text.trimToNull(routeHostname);
        deploymentPath = Text.trimToNull(deploymentPath);
        deploymentRepoUrl = Text.trimToNull(deploymentRepoUrl);
        deploymentRepoBranch = Text.trimToNull(deploymentRepoBranch);
        deploymentRepoCredentialsId = Text.trimToNull(deploymentRepoCredentialsId);
    }

    public boolean isEmpty() {
        return toConfig().isEmpty();
    }

    public Map<String, Object> toConfig() {
        ConfigTree entry = new ConfigTree()
                .set("projectBuildR", projectBuild)
                .set("buildConfigPath", buildConfigPath)
                .set("dockerFilePath", dockerFilePath)
                .set("buildContext", buildContext)
                .set("addFile", addFile)
                .set("qcDockerRepoPush", dockerRepoPush)
                .set("qcDockerRepoPull", dockerRepoPull)
                .set("openshiftCertDir", certDir)
                .set("nexus.authfile", nexusAuthFile)
                .set("projectDeploymentR", projectDeployment)
                .set("deployConfigPath", deployConfigPath)
                .set("configPathR", configPath)
                .set("healthCheckUrl", healthCheckUrl)
                .set("routeHostnameR", routeHostname)
                .set("deploymentPath", deploymentPath)
                .set("deploymentRepo.url", deploymentRepoUrl)
                .set("deploymentRepo.branch", deploymentRepoBranch)
                .set("deploymentRepo.credentials", deploymentRepoCredentialsId);
        if (skipConfigDeploy) {
            entry.set("skipConfigDeploy", true);
        }
        return entry.toMap();
    }
}
