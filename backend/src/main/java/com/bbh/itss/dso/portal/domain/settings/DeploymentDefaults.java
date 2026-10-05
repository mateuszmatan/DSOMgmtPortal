package com.bbh.itss.dso.portal.domain.settings;

import com.bbh.itss.dso.portal.domain.catalog.DeployTarget;
import com.bbh.itss.dso.portal.domain.catalog.Region;
import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;

public record DeploymentDefaults(
        String urbanCodeSiteName,
        String urbanCodeDeployProcess,
        String rdHost,
        String qcHost,
        String sshUser,
        String deployScript,
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

    public void fillIn(ConfigTree config, DeployTarget target) {
        if (target != DeployTarget.VM) {
            return;
        }
        config.fillIn("deploy.vm.dod", "siteName", urbanCodeSiteName)
                .fillIn("deploy.vm.dod", "deployProcess", urbanCodeDeployProcess);
        for (Region region : Region.values()) {
            String path = "deploy.vm." + region.configKey() + ".";
            config.setIfAbsent(path + "host", region == Region.RD ? rdHost : qcHost)
                    .setIfAbsent(path + "user", sshUser)
                    .setIfAbsent(path + "deployScript", deployScript)
                    .setIfAbsent(path + "versionFile", versionFile);
        }
    }
}
