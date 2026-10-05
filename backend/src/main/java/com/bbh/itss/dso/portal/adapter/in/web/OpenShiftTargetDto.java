package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.OpenShiftTarget;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OpenShiftTargetDto(
        @Size(max = 200) String projectBuild,
        @Size(max = 500) String buildConfigPath,
        @Size(max = 500) String dockerFilePath,
        @Size(max = 500) String buildContext,
        @Size(max = 500) String addFile,
        @Size(max = 500) String dockerRepoPush,
        @Size(max = 500) String dockerRepoPull,
        @Size(max = 500) String certDir,
        @Size(max = 500) String nexusAuthFile,
        @Size(max = 200) String projectDeployment,
        @Size(max = 500) String deployConfigPath,
        @Size(max = 500) String configPath,
        Boolean skipConfigDeploy,
        @Size(max = 500) String healthCheckUrl,
        @Size(max = 300) String routeHostname,
        @Size(max = 500) String deploymentPath,
        @Size(max = 1000)
        @Pattern(regexp = "^(https?://\\S+|ssh://\\S+|git@\\S+)?$", message = "must be a Git repository URL")
        String deploymentRepoUrl,
        @Size(max = 200) String deploymentRepoBranch,
        @Size(max = 200) String deploymentRepoCredentialsId) {

    public OpenShiftTargetDto {
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

    static OpenShiftTargetDto from(OpenShiftTarget source) {
        return new OpenShiftTargetDto(source.projectBuild(), source.buildConfigPath(), source.dockerFilePath(),
                source.buildContext(), source.addFile(), source.dockerRepoPush(), source.dockerRepoPull(),
                source.certDir(), source.nexusAuthFile(), source.projectDeployment(), source.deployConfigPath(),
                source.configPath(), source.skipConfigDeploy(), source.healthCheckUrl(), source.routeHostname(),
                source.deploymentPath(), source.deploymentRepoUrl(), source.deploymentRepoBranch(),
                source.deploymentRepoCredentialsId());
    }

    OpenShiftTarget toDomain() {
        return new OpenShiftTarget(projectBuild, buildConfigPath, dockerFilePath, buildContext, addFile, dockerRepoPush,
                dockerRepoPull, certDir, nexusAuthFile, projectDeployment, deployConfigPath, configPath,
                skipConfigDeploy, healthCheckUrl, routeHostname, deploymentPath, deploymentRepoUrl,
                deploymentRepoBranch, deploymentRepoCredentialsId);
    }
}
