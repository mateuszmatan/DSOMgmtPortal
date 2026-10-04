package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Embeddable
public record AppScanAccount(
        @NotBlank @Size(max = 200)
        @Column(name = "ASOC_KEY_ID", nullable = false, length = 200)
        String keyId,
        @Size(max = 200)
        @Column(name = "ASOC_SECRET_CREDENTIALS_ID", length = 200)
        String secretCredentialsId) implements ConfigSection {

    public AppScanAccount {
        keyId = Text.trimToNull(keyId);
        secretCredentialsId = Text.trimToNull(secretCredentialsId);
    }

    @Override
    public void writeTo(ConfigTree config) {
        config.set("asoc.keyId", keyId).set("asoc.token", secretCredentialsId);
    }
}
