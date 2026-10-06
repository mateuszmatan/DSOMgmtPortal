package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.Map;

public record OpenShiftTarget(String projectBuild, String buildConfigPath, String dockerFilePath, String buildContext,
                              String addFile, String dockerRepoPush, String dockerRepoPull, String certDir,
                              String nexusAuthFile, String projectDeployment, String deployConfigPath,
                              String configPath, Boolean skipConfigDeploy, String healthCheckUrl, String routeHostname,
                              String deploymentPath, String deploymentRepoUrl, String deploymentRepoBranch,
                              String deploymentRepoCredentialsId, String buildTag, String internalDockerUrl) {

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
        buildTag = Text.trimToNull(buildTag);
        internalDockerUrl = Text.trimToNull(internalDockerUrl);
    }

    public static final OpenShiftTarget NONE = new OpenShiftTarget(null, null, null, null, null, null, null, null, null,
            null, null, null, false, null, null, null, null, null, null, null, null);

    public void validateImageBuild(ValidationProblems problems) {
        String message = "is required for OpenShift: the Nexus snapshot delivery builds the image in the RD project";
        problems.require("projectBuild", projectBuild, message).require("buildConfigPath", buildConfigPath, message)
                .require("dockerFilePath", dockerFilePath, message).require("buildContext", buildContext, message)
                .require("dockerRepoPush", dockerRepoPush, message).require("nexusAuthFile", nexusAuthFile, message);
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
                .set("deploymentRepo.credentials", deploymentRepoCredentialsId)
                .set("buildTag", buildTag)
                .set("internalDockerUrl", internalDockerUrl)
                .flag("skipConfigDeploy", skipConfigDeploy);
        return entry.toMap();
    }
}
