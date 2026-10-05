package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.catalog.SshTarget;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record SshTargetEmbeddable(
        @Column(name = "HOST", length = 255) String host,
        @Column(name = "SSH_USER", length = 100) String user,
        @Column(name = "DEPLOY_DIR", length = 500) String deployDir,
        @Column(name = "DEPLOY_SCRIPT", length = 500) String deployScript,
        @Column(name = "VERSION_FILE", length = 500) String versionFile) {

    static SshTargetEmbeddable of(SshTarget target) {
        return new SshTargetEmbeddable(target.host(), target.user(), target.deployDir(), target.deployScript(),
                target.versionFile());
    }

    SshTarget toDomain() {
        return new SshTarget(host, user, deployDir, deployScript, versionFile);
    }
}
