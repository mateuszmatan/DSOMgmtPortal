package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigSection;
import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;

import java.util.List;

public record NexusIqSettings(
        String application,
        List<String> scanPatterns,
        String stage,
        Boolean failOnNetworkError,
        String scaScanName) implements ConfigSection {

    public static final String DEFAULT_STAGE = "build";
    public static final NexusIqSettings NONE = new NexusIqSettings(null, List.of(), null, false, null);

    public NexusIqSettings {
        application = Text.trimToNull(application);
        scanPatterns = Text.clean(scanPatterns);
        stage = Text.orDefault(stage, DEFAULT_STAGE);
        failOnNetworkError = Boolean.TRUE.equals(failOnNetworkError);
        scaScanName = Text.trimToNull(scaScanName);
    }

    public static NexusIqSettings of(String application, List<String> scanPatterns) {
        return new NexusIqSettings(application, scanPatterns, null, false, null);
    }

    @Override
    public void writeTo(ConfigTree config) {
        config.set("tools.nexusIq.application", application)
                .set("tools.nexusIq.scanPatterns", scanPatterns)
                .set("tools.nexusIq.stage", stage)
                .set("tools.nexusIq.failOnNetworkError", failOnNetworkError)
                .set("sca.scanName", scaScanName);
    }
}
