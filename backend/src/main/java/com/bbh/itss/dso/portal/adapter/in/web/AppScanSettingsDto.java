package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.AppScanSettings;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Locale;

import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.URL;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.URL_MESSAGE;

public record AppScanSettingsDto(
        @NotBlank
        @Pattern(regexp = "^\\s*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\s*$",
                message = "must be the AppScan application ID, a UUID such as 109f44ac-cc06-4ca0-884e-d944904f7019")
        String applicationId,
        @Size(max = 200)
        String sastScanName,
        @Size(max = 30)
        List<@Pattern(regexp = "^[^,]{1,300}$", message = "one folder per entry, without commas") String> includedDirs,
        @Size(max = 30)
        List<@Pattern(regexp = "^[^,]{1,300}$", message = "one folder per entry, without commas") String> excludedDirs,
        Boolean compile,
        Boolean sourceCodeOnly,
        Boolean useConfigFile,
        Boolean insecureTls,
        @Size(max = 500)
        String clientPath,
        @Valid
        ToolCommandDto compileCommand,
        Boolean dastEnabled,
        @Size(max = 200)
        String dastScanName,
        @Size(max = 1000)
        @Pattern(regexp = URL, message = URL_MESSAGE)
        String dastTargetUrl,
        @Size(max = 100)
        String dastPresenceId) {

    public AppScanSettingsDto {
        applicationId = applicationId == null ? null : applicationId.trim().toLowerCase(Locale.ROOT);
        sastScanName = Text.trimToNull(sastScanName);
        includedDirs = Text.clean(includedDirs);
        excludedDirs = Text.clean(excludedDirs);
        compile = !Boolean.FALSE.equals(compile);
        sourceCodeOnly = Boolean.TRUE.equals(sourceCodeOnly);
        useConfigFile = Boolean.TRUE.equals(useConfigFile);
        insecureTls = Boolean.TRUE.equals(insecureTls);
        clientPath = Text.trimToNull(clientPath);
        compileCommand = compileCommand == null ? ToolCommandDto.NONE : compileCommand;
        dastEnabled = Boolean.TRUE.equals(dastEnabled);
        dastScanName = Text.trimToNull(dastScanName);
        dastTargetUrl = Text.trimToNull(dastTargetUrl);
        dastPresenceId = Text.trimToNull(dastPresenceId);
    }

    static AppScanSettingsDto from(AppScanSettings source) {
        return new AppScanSettingsDto(source.applicationId(), source.sastScanName(), source.includedDirs(),
                source.excludedDirs(), source.compile(), source.sourceCodeOnly(), source.useConfigFile(),
                source.insecureTls(), source.clientPath(), ToolCommandDto.from(source.compileCommand()),
                source.dastEnabled(), source.dastScanName(), source.dastTargetUrl(), source.dastPresenceId());
    }

    AppScanSettings toDomain() {
        return new AppScanSettings(applicationId, sastScanName, includedDirs, excludedDirs, compile, sourceCodeOnly,
                useConfigFile, insecureTls, clientPath, compileCommand.toDomain(), dastEnabled, dastScanName,
                dastTargetUrl, dastPresenceId);
    }
}
