package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.settings.DeploymentDefaults;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record DeploymentDefaultsEmbeddable(
        @Column(name = "UCD_SITE_NAME", nullable = false, length = 200) String urbanCodeSiteName,
        @Column(name = "UCD_DEPLOY_PROCESS", nullable = false, length = 200) String urbanCodeDeployProcess,
        @Column(name = "RD_HOST", nullable = false, length = 255) String rdHost,
        @Column(name = "QC_HOST", nullable = false, length = 255) String qcHost,
        @Column(name = "SSH_USER", nullable = false, length = 100) String sshUser,
        @Column(name = "DEPLOY_SCRIPT", nullable = false, length = 500) String deployScript,
        @Column(name = "VERSION_FILE", nullable = false, length = 500) String versionFile) {

    static DeploymentDefaultsEmbeddable of(DeploymentDefaults deployment) {
        return new DeploymentDefaultsEmbeddable(deployment.urbanCodeSiteName(), deployment.urbanCodeDeployProcess(),
                deployment.rdHost(), deployment.qcHost(), deployment.sshUser(), deployment.deployScript(),
                deployment.versionFile());
    }

    DeploymentDefaults toDomain() {
        return new DeploymentDefaults(urbanCodeSiteName, urbanCodeDeployProcess, rdHost, qcHost, sshUser, deployScript,
                versionFile);
    }
}
