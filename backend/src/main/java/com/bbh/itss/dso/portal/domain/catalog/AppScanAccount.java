package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;

public record AppScanAccount(String keyId, String secretCredentialsId) {

    public AppScanAccount {
        keyId = Text.trimToNull(keyId);
        secretCredentialsId = Text.trimToNull(secretCredentialsId);
    }

    public void writeTo(ConfigTree config) {
        config.set("asoc.keyId", keyId).setIfAbsent("asoc.token", secretCredentialsId);
    }
}
