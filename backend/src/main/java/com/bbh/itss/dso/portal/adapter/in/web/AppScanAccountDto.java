package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.AppScanAccount;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppScanAccountDto(
        @NotBlank @Size(max = 200)
        String keyId,
        @Size(max = 200)
        String secretCredentialsId) {

    public AppScanAccountDto {
        keyId = Text.trimToNull(keyId);
        secretCredentialsId = Text.trimToNull(secretCredentialsId);
    }

    static AppScanAccountDto from(AppScanAccount source) {
        return new AppScanAccountDto(source.keyId(), source.secretCredentialsId());
    }

    AppScanAccount toDomain() {
        return new AppScanAccount(keyId, secretCredentialsId);
    }
}
