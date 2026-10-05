package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.catalog.AppScanAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record AppScanAccountEmbeddable(
        @Column(name = "ASOC_KEY_ID", nullable = false, length = 200) String keyId,
        @Column(name = "ASOC_SECRET_CREDENTIALS_ID", length = 200) String secretCredentialsId) {

    static AppScanAccountEmbeddable of(AppScanAccount account) {
        return new AppScanAccountEmbeddable(account.keyId(), account.secretCredentialsId());
    }

    AppScanAccount toDomain() {
        return new AppScanAccount(keyId, secretCredentialsId);
    }
}
