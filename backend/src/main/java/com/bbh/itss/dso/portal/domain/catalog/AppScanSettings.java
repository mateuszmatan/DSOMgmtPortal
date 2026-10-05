package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.StoredList;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.List;
import java.util.Locale;

public record AppScanSettings(String applicationId, String sastScanName, List<String> includedDirs,
                              List<String> excludedDirs, Boolean compile, Boolean sourceCodeOnly, Boolean useConfigFile,
                              Boolean insecureTls, String clientPath, ToolCommand compileCommand, Boolean dastEnabled,
                              String dastScanName, String dastTargetUrl, String dastPresenceId) {

    public AppScanSettings {
        applicationId = applicationId == null ? null : applicationId.trim().toLowerCase(Locale.ROOT);
        sastScanName = Text.trimToNull(sastScanName);
        includedDirs = Text.clean(includedDirs);
        excludedDirs = Text.clean(excludedDirs);
        compile = !Boolean.FALSE.equals(compile);
        sourceCodeOnly = Boolean.TRUE.equals(sourceCodeOnly);
        useConfigFile = Boolean.TRUE.equals(useConfigFile);
        insecureTls = Boolean.TRUE.equals(insecureTls);
        clientPath = Text.trimToNull(clientPath);
        compileCommand = compileCommand == null ? ToolCommand.NONE : compileCommand;
        dastEnabled = Boolean.TRUE.equals(dastEnabled);
        dastScanName = Text.trimToNull(dastScanName);
        dastTargetUrl = Text.trimToNull(dastTargetUrl);
        dastPresenceId = Text.trimToNull(dastPresenceId);
    }

    public static AppScanSettings of(String applicationId) {
        return new AppScanSettings(applicationId, null, List.of(), List.of(), true, false, false, false, null, null,
                false, null, null, null);
    }

    public void writeTo(ConfigTree config, BuildTool tool) {
        config.set("appId", applicationId)
                .set("includedDirs", String.join(",", includedDirs))
                .set("excludedDirs", String.join(",", excludedDirs))
                .set("appscanPath", clientPath)
                .set("asoc.doCompile", compile ? null : false)
                .flag("asoc.sourceCodeOnly", sourceCodeOnly)
                .flag("asoc.useAppScanConfig", useConfigFile)
                .flag("asoc.insecureTls", insecureTls);
        compileCommand.writeTo(config, "asoc", tool);
        config.set("sast.scanName", sastScanName)
                .set("dast.enabled", dastEnabled)
                .set("dast.scanName", dastScanName)
                .set("dast.targetUrl", dastTargetUrl)
                .set("dast.presenceId", dastPresenceId);
    }

    public void validate(ValidationProblems problems) {
        if (dastEnabled && dastTargetUrl == null) {
            problems.add("dastTargetUrl", "is required when DAST is enabled");
        }
        StoredList.LINES_2000.check(problems, "includedDirs", includedDirs);
        StoredList.LINES_2000.check(problems, "excludedDirs", excludedDirs);
        compileCommand.validate(problems.at("compileCommand"));
    }
}
