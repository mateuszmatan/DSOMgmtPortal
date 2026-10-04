package com.bbh.dso.portal.catalog;

import com.bbh.dso.portal.common.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The HCL AppScan on Cloud API key a product's services use. Only the key ID is stored; the secret stays in
 * the Jenkins credential named here ({@code asoc.keyId} and {@code asoc.token}).
 */
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
