package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.StoredList;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.List;
import java.util.Locale;

import static org.apache.commons.lang3.StringUtils.trimToNull;

public record AppScanSettings(String applicationId, String sastScanName, List<String> includedDirs,
                              List<String> excludedDirs, Boolean compile, Boolean sourceCodeOnly, Boolean useConfigFile,
                              Boolean insecureTls, String clientPath, ToolCommand compileCommand, Boolean dastEnabled,
                              String dastScanName, String dastTargetUrl, String dastPresenceId,
                              String secretCredentialsId) {

    public AppScanSettings {
        applicationId = applicationId == null ? null : applicationId.trim().toLowerCase(Locale.ROOT);
        sastScanName = trimToNull(sastScanName);
        includedDirs = Text.clean(includedDirs);
        excludedDirs = Text.clean(excludedDirs);
        compile = !Boolean.FALSE.equals(compile);
        sourceCodeOnly = Boolean.TRUE.equals(sourceCodeOnly);
        useConfigFile = Boolean.TRUE.equals(useConfigFile);
        insecureTls = Boolean.TRUE.equals(insecureTls);
        clientPath = trimToNull(clientPath);
        compileCommand = compileCommand == null ? ToolCommand.NONE : compileCommand;
        dastEnabled = Boolean.TRUE.equals(dastEnabled);
        dastScanName = trimToNull(dastScanName);
        dastTargetUrl = trimToNull(dastTargetUrl);
        dastPresenceId = trimToNull(dastPresenceId);
        secretCredentialsId = trimToNull(secretCredentialsId);
    }

    public static AppScanSettings of(String applicationId) {
        return new AppScanSettings(applicationId, null, List.of(), List.of(), true, false, false, false, null, null,
                false, null, null, null, null);
    }

    public void writeTo(ConfigTree config, BuildTool tool) {
        config.set("appId", applicationId)
                .set("includedDirs", String.join(",", includedDirs))
                .set("excludedDirs", String.join(",", excludedDirs))
                .set("appscanPath", clientPath)
                .set("asoc.token", secretCredentialsId)
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

    public void validate(ValidationProblems problems, BuildTool tool) {
        if (dastEnabled && dastTargetUrl == null) {
            problems.add("dastTargetUrl", "is required when DAST is enabled");
        }
        if (compile && tool != BuildTool.FLUTTER && !compileCommand.isEmpty() && compileCommand.tasks().isEmpty()) {
            problems.add("compileCommand.tasks", tool == BuildTool.MAVEN
                    ? "add the Maven goals that compile the code for the scan, or clear the command to use the build goals"
                    : "add the Gradle tasks that compile the code for the scan, or clear the command to use the build tasks");
        }
        StoredList.LINES_2000.check(problems, "includedDirs", includedDirs);
        StoredList.LINES_2000.check(problems, "excludedDirs", excludedDirs);
        compileCommand.validate(problems.at("compileCommand"));
    }
}
