package com.bbh.itss.dso.portal.settings;

import com.bbh.itss.dso.portal.catalog.ConfigTree;
import com.bbh.itss.dso.portal.catalog.Region;
import com.bbh.itss.dso.portal.common.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Embeddable
public record DeploymentDefaults(
        @NotBlank @Size(max = 200)
        @Column(name = "UCD_SITE_NAME", nullable = false, length = 200)
        String urbanCodeSiteName,
        @NotBlank @Size(max = 200)
        @Column(name = "UCD_DEPLOY_PROCESS", nullable = false, length = 200)
        String urbanCodeDeployProcess,
        @NotBlank @Size(max = 255) @Pattern(regexp = PlatformSettings.HOST, message = PlatformSettings.HOST_MESSAGE)
        @Column(name = "RD_HOST", nullable = false, length = 255)
        String rdHost,
        @NotBlank @Size(max = 255) @Pattern(regexp = PlatformSettings.HOST, message = PlatformSettings.HOST_MESSAGE)
        @Column(name = "QC_HOST", nullable = false, length = 255)
        String qcHost,
        @NotBlank @Size(max = 100)
        @Column(name = "SSH_USER", nullable = false, length = 100)
        String sshUser,
        @NotBlank @Size(max = 500)
        @Column(name = "DEPLOY_SCRIPT", nullable = false, length = 500)
        String deployScript,
        @NotBlank @Size(max = 500)
        @Column(name = "VERSION_FILE", nullable = false, length = 500)
        String versionFile) {

    public DeploymentDefaults {
        urbanCodeSiteName = Text.trimToNull(urbanCodeSiteName);
        urbanCodeDeployProcess = Text.trimToNull(urbanCodeDeployProcess);
        rdHost = Text.trimToNull(rdHost);
        qcHost = Text.trimToNull(qcHost);
        sshUser = Text.trimToNull(sshUser);
        deployScript = Text.trimToNull(deployScript);
        versionFile = Text.trimToNull(versionFile);
    }

    public void fillIn(ConfigTree config) {
        config.fillIn("deploy.vm.dod", "siteName", urbanCodeSiteName)
                .fillIn("deploy.vm.dod", "deployProcess", urbanCodeDeployProcess);
        for (Region region : Region.values()) {
            String path = "deploy.vm." + region.configKey();
            config.fillIn(path, "host", region == Region.RD ? rdHost : qcHost)
                    .fillIn(path, "user", sshUser)
                    .fillIn(path, "deployScript", deployScript)
                    .fillIn(path, "versionFile", versionFile);
        }
    }
}
