package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.Text;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

@Embeddable
public record SshTarget(
        @Size(max = 255)
        @Pattern(regexp = "^[A-Za-z0-9.-]*$", message = "must be a host name such as rdltaapps1.testbbh.com")
        @Column(name = "HOST", length = 255)
        String host,
        @Size(max = 100)
        @Column(name = "SSH_USER", length = 100)
        String user,
        @Size(max = 500)
        @Column(name = "DEPLOY_DIR", length = 500)
        String deployDir,
        @Size(max = 500)
        @Column(name = "DEPLOY_SCRIPT", length = 500)
        String deployScript,
        @Size(max = 500)
        @Column(name = "VERSION_FILE", length = 500)
        String versionFile) {

    public SshTarget {
        host = Text.trimToNull(host);
        user = Text.trimToNull(user);
        deployDir = Text.trimToNull(deployDir);
        deployScript = Text.trimToNull(deployScript);
        versionFile = Text.trimToNull(versionFile);
    }

    @JsonIgnore
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
