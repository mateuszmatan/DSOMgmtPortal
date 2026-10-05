package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;

import java.util.Map;

public record SshTarget(String host, String user, String deployDir, String deployScript, String versionFile) {

    public SshTarget {
        host = Text.trimToNull(host);
        user = Text.trimToNull(user);
        deployDir = Text.trimToNull(deployDir);
        deployScript = Text.trimToNull(deployScript);
        versionFile = Text.trimToNull(versionFile);
    }

    public boolean isEmpty() {
        return toConfig().isEmpty();
    }

    public Map<String, Object> toConfig() {
        return new ConfigTree()
                .set("host", host)
                .set("user", user)
                .set("deployDir", deployDir)
                .set("deployScript", deployScript)
                .set("versionFile", versionFile)
                .toMap();
    }
}
