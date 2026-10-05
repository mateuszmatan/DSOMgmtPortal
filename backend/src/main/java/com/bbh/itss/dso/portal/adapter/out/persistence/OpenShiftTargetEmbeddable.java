package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.catalog.OpenShiftTarget;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import org.hibernate.type.NumericBooleanConverter;

@Embeddable
public record OpenShiftTargetEmbeddable(
        @Column(name = "PROJECT_BUILD", length = 200) String projectBuild,
        @Column(name = "BUILD_CONFIG_PATH", length = 500) String buildConfigPath,
        @Column(name = "DOCKER_FILE_PATH", length = 500) String dockerFilePath,
        @Column(name = "BUILD_CONTEXT", length = 500) String buildContext,
        @Column(name = "ADD_FILE", length = 500) String addFile,
        @Column(name = "DOCKER_REPO_PUSH", length = 500) String dockerRepoPush,
        @Column(name = "DOCKER_REPO_PULL", length = 500) String dockerRepoPull,
        @Column(name = "CERT_DIR", length = 500) String certDir,
        @Column(name = "NEXUS_AUTH_FILE", length = 500) String nexusAuthFile,
        @Column(name = "PROJECT_DEPLOYMENT", length = 200) String projectDeployment,
        @Column(name = "DEPLOY_CONFIG_PATH", length = 500) String deployConfigPath,
        @Column(name = "CONFIG_PATH", length = 500) String configPath,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "SKIP_CONFIG_DEPLOY", nullable = false) Boolean skipConfigDeploy,
        @Column(name = "HEALTH_CHECK_URL", length = 500) String healthCheckUrl,
        @Column(name = "ROUTE_HOSTNAME", length = 300) String routeHostname,
        @Column(name = "DEPLOYMENT_PATH", length = 500) String deploymentPath,
        @Column(name = "DEPLOYMENT_REPO_URL", length = 1000) String deploymentRepoUrl,
        @Column(name = "DEPLOYMENT_REPO_BRANCH", length = 200) String deploymentRepoBranch,
        @Column(name = "DEPLOYMENT_REPO_CREDENTIALS_ID", length = 200) String deploymentRepoCredentialsId) {

    static OpenShiftTargetEmbeddable of(OpenShiftTarget target) {
        return new OpenShiftTargetEmbeddable(target.projectBuild(), target.buildConfigPath(), target.dockerFilePath(),
                target.buildContext(), target.addFile(), target.dockerRepoPush(), target.dockerRepoPull(),
                target.certDir(), target.nexusAuthFile(), target.projectDeployment(), target.deployConfigPath(),
                target.configPath(), target.skipConfigDeploy(), target.healthCheckUrl(), target.routeHostname(),
                target.deploymentPath(), target.deploymentRepoUrl(), target.deploymentRepoBranch(),
                target.deploymentRepoCredentialsId());
    }

    OpenShiftTarget toDomain() {
        return new OpenShiftTarget(projectBuild, buildConfigPath, dockerFilePath, buildContext, addFile, dockerRepoPush,
                dockerRepoPull, certDir, nexusAuthFile, projectDeployment, deployConfigPath, configPath,
                skipConfigDeploy, healthCheckUrl, routeHostname, deploymentPath, deploymentRepoUrl,
                deploymentRepoBranch, deploymentRepoCredentialsId);
    }
}
