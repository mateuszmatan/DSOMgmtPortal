package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.NexusIqSettings;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record NexusIqSettingsDto(
        @Size(max = 200)
        String application,
        @Size(max = 20)
        List<@NotBlank @Size(max = 300) String> scanPatterns,
        @Size(max = 50)
        @Pattern(regexp = "^[a-z-]*$", message = "must be a Nexus IQ stage such as build, stage-release or release")
        String stage,
        Boolean failOnNetworkError,
        @Size(max = 200)
        String scaScanName) {

    public NexusIqSettingsDto {
        application = Text.trimToNull(application);
        scanPatterns = Text.clean(scanPatterns);
        stage = Text.orDefault(stage, NexusIqSettings.DEFAULT_STAGE);
        failOnNetworkError = Boolean.TRUE.equals(failOnNetworkError);
        scaScanName = Text.trimToNull(scaScanName);
    }

    static NexusIqSettingsDto from(NexusIqSettings source) {
        return new NexusIqSettingsDto(source.application(), source.scanPatterns(), source.stage(),
                source.failOnNetworkError(), source.scaScanName());
    }

    NexusIqSettings toDomain() {
        return new NexusIqSettings(application, scanPatterns, stage, failOnNetworkError, scaScanName);
    }
}
