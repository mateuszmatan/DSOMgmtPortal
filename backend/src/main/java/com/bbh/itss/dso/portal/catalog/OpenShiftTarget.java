package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.Text;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.type.NumericBooleanConverter;

import java.util.Map;

@Embeddable
public record OpenShiftTarget(
        @Size(max = 200) @Column(name = "PROJECT_BUILD", length = 200) String projectBuild,
        @Size(max = 500) @Column(name = "BUILD_CONFIG_PATH", length = 500) String buildConfigPath,
        @Size(max = 500) @Column(name = "DOCKER_FILE_PATH", length = 500) String dockerFilePath,
        @Size(max = 500) @Column(name = "BUILD_CONTEXT", length = 500) String buildContext,
        @Size(max = 500) @Column(name = "ADD_FILE", length = 500) String addFile,
        @Size(max = 500) @Column(name = "DOCKER_REPO_PUSH", length = 500) String dockerRepoPush,
        @Size(max = 500) @Column(name = "DOCKER_REPO_PULL", length = 500) String dockerRepoPull,
        @Size(max = 500) @Column(name = "CERT_DIR", length = 500) String certDir,
        @Size(max = 500) @Column(name = "NEXUS_AUTH_FILE", length = 500) String nexusAuthFile,
        @Size(max = 200) @Column(name = "PROJECT_DEPLOYMENT", length = 200) String projectDeployment,
        @Size(max = 500) @Column(name = "DEPLOY_CONFIG_PATH", length = 500) String deployConfigPath,
        @Size(max = 500) @Column(name = "CONFIG_PATH", length = 500) String configPath,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "SKIP_CONFIG_DEPLOY", nullable = false) Boolean skipConfigDeploy,
        @Size(max = 500) @Column(name = "HEALTH_CHECK_URL", length = 500) String healthCheckUrl,
        @Size(max = 300) @Column(name = "ROUTE_HOSTNAME", length = 300) String routeHostname,
        @Size(max = 500) @Column(name = "DEPLOYMENT_PATH", length = 500) String deploymentPath,
        @Size(max = 1000)
        @Pattern(regexp = "^(https?://\\S+|ssh://\\S+|git@\\S+)?$", message = "must be a Git repository URL")
        @Column(name = "DEPLOYMENT_REPO_URL", length = 1000) String deploymentRepoUrl,
        @Size(max = 200) @Column(name = "DEPLOYMENT_REPO_BRANCH", length = 200) String deploymentRepoBranch,
        @Size(max = 200) @Column(name = "DEPLOYMENT_REPO_CREDENTIALS_ID", length = 200) String deploymentRepoCredentialsId) {

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

    @JsonIgnore
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
