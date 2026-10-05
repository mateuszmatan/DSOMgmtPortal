package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.List;

public record GoldenFixPolicy(
        Boolean enabled,
        Boolean onlyDirectDependencies,
        Integer minThreatLevel,
        List<String> ecosystems,
        List<String> goldenVersionTypes,
        List<String> excludeDirs,
        Boolean verifyEnabled,
        Integer verifyMaxAttempts,
        Integer verifyTimeoutMinutes,
        String verifyMavenCommand,
        String verifyGradleCommand,
        String verifyNpmCommand,
        String verifyPipCommand,
        String verifyPubCommand,
        String commitAuthorName,
        String commitAuthorEmail,
        String timeZone) {

    public static final GoldenFixPolicy INHERITED = inherit(true);

    public GoldenFixPolicy {
        enabled = !Boolean.FALSE.equals(enabled);
        ecosystems = Text.clean(ecosystems);
        goldenVersionTypes = Text.clean(goldenVersionTypes);
        excludeDirs = Text.clean(excludeDirs);
        verifyMavenCommand = Text.trimToNull(verifyMavenCommand);
        verifyGradleCommand = Text.trimToNull(verifyGradleCommand);
        verifyNpmCommand = Text.trimToNull(verifyNpmCommand);
        verifyPipCommand = Text.trimToNull(verifyPipCommand);
        verifyPubCommand = Text.trimToNull(verifyPubCommand);
        commitAuthorName = Text.trimToNull(commitAuthorName);
        commitAuthorEmail = Text.trimToNull(commitAuthorEmail);
        timeZone = Text.trimToNull(timeZone);
    }

    public static GoldenFixPolicy inherit(boolean enabled) {
        return new GoldenFixPolicy(enabled, null, null, List.of(), List.of(), List.of(), null, null, null, null, null,
                null, null, null, null, null, null);
    }

    public void writeTo(ConfigTree config) {
        config.set("goldenFix.enabled", enabled)
                .set("goldenFix.onlyDirectDependencies", onlyDirectDependencies)
                .set("goldenFix.minThreatLevel", minThreatLevel)
                .set("goldenFix.ecosystems", ecosystems)
                .set("goldenFix.goldenVersionTypes", goldenVersionTypes)
                .set("goldenFix.excludeDirs", excludeDirs)
                .set("goldenFix.verify.enabled", verifyEnabled)
                .set("goldenFix.verify.maxAttempts", verifyMaxAttempts)
                .set("goldenFix.verify.timeoutMinutes", verifyTimeoutMinutes)
                .set("goldenFix.verify.commands.maven", verifyMavenCommand)
                .set("goldenFix.verify.commands.gradle", verifyGradleCommand)
                .set("goldenFix.verify.commands.npm", verifyNpmCommand)
                .set("goldenFix.verify.commands.pip", verifyPipCommand)
                .set("goldenFix.verify.commands.pub", verifyPubCommand)
                .set("goldenFix.commitAuthorName", commitAuthorName)
                .set("goldenFix.commitAuthorEmail", commitAuthorEmail)
                .set("goldenFix.timeZone", timeZone);
    }

    public void validateComplete(ValidationProblems problems) {
        require(problems, "onlyDirectDependencies", onlyDirectDependencies);
        require(problems, "minThreatLevel", minThreatLevel);
        require(problems, "verifyEnabled", verifyEnabled);
        require(problems, "verifyMaxAttempts", verifyMaxAttempts);
        require(problems, "verifyTimeoutMinutes", verifyTimeoutMinutes);
        require(problems, "commitAuthorName", commitAuthorName);
        require(problems, "commitAuthorEmail", commitAuthorEmail);
        if (ecosystems.isEmpty()) {
            problems.add("ecosystems", "select at least one ecosystem");
        }
        if (goldenVersionTypes.isEmpty()) {
            problems.add("goldenVersionTypes", "add at least one remediation type");
        }
    }

    private static void require(ValidationProblems problems, String field, Object value) {
        if (value == null) {
            problems.add(field, "is required in the global settings");
        }
    }
}
