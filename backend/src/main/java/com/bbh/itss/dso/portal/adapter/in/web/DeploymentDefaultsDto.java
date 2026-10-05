package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.settings.DeploymentDefaults;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.HOST;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.HOST_MESSAGE;

public record DeploymentDefaultsDto(
        @NotBlank @Size(max = 200)
        String urbanCodeSiteName,
        @NotBlank @Size(max = 200)
        String urbanCodeDeployProcess,
        @NotBlank @Size(max = 255) @Pattern(regexp = HOST, message = HOST_MESSAGE)
        String rdHost,
        @NotBlank @Size(max = 255) @Pattern(regexp = HOST, message = HOST_MESSAGE)
        String qcHost,
        @NotBlank @Size(max = 100)
        String sshUser,
        @NotBlank @Size(max = 500)
        String deployScript,
        @NotBlank @Size(max = 500)
        String versionFile) {

    public DeploymentDefaultsDto {
        urbanCodeSiteName = Text.trimToNull(urbanCodeSiteName);
        urbanCodeDeployProcess = Text.trimToNull(urbanCodeDeployProcess);
        rdHost = Text.trimToNull(rdHost);
        qcHost = Text.trimToNull(qcHost);
        sshUser = Text.trimToNull(sshUser);
        deployScript = Text.trimToNull(deployScript);
        versionFile = Text.trimToNull(versionFile);
    }

    static DeploymentDefaultsDto from(DeploymentDefaults deployment) {
        return new DeploymentDefaultsDto(deployment.urbanCodeSiteName(), deployment.urbanCodeDeployProcess(),
                deployment.rdHost(), deployment.qcHost(), deployment.sshUser(), deployment.deployScript(),
                deployment.versionFile());
    }

    DeploymentDefaults toDomain() {
        return new DeploymentDefaults(urbanCodeSiteName, urbanCodeDeployProcess, rdHost, qcHost, sshUser, deployScript,
                versionFile);
    }
}
