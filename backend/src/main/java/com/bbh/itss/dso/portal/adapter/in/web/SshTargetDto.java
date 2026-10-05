package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.SshTarget;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SshTargetDto(
        @Size(max = 255)
        @Pattern(regexp = "^[A-Za-z0-9.-]*$", message = "must be a host name such as rdltaapps1.testbbh.com")
        String host,
        @Size(max = 100)
        String user,
        @Size(max = 500)
        String deployDir,
        @Size(max = 500)
        String deployScript,
        @Size(max = 500)
        String versionFile) {

    public SshTargetDto {
        host = Text.trimToNull(host);
        user = Text.trimToNull(user);
        deployDir = Text.trimToNull(deployDir);
        deployScript = Text.trimToNull(deployScript);
        versionFile = Text.trimToNull(versionFile);
    }

    static SshTargetDto from(SshTarget source) {
        return new SshTargetDto(source.host(), source.user(), source.deployDir(), source.deployScript(),
                source.versionFile());
    }

    SshTarget toDomain() {
        return new SshTarget(host, user, deployDir, deployScript, versionFile);
    }
}
