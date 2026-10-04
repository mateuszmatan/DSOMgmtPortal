package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.DelimitedListConverter;
import com.bbh.itss.dso.portal.common.Text;
import com.bbh.itss.dso.portal.common.ValidationProblems;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.type.NumericBooleanConverter;

import java.util.List;

@Embeddable
public record GoldenFixPolicy(
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "GOLDEN_FIX_ENABLED", nullable = false)
        Boolean enabled,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "GOLDEN_FIX_DIRECT_ONLY")
        Boolean onlyDirectDependencies,
        @Min(1) @Max(10)
        @Column(name = "GOLDEN_FIX_MIN_THREAT_LEVEL")
        Integer minThreatLevel,
        @Size(max = 4)
        @Convert(converter = DelimitedListConverter.Commas.class)
        @Column(name = "GOLDEN_FIX_ECOSYSTEMS", length = 200)
        List<@Pattern(regexp = "^(maven|npm|pypi|pub)$", message = "must be maven, npm, pypi or pub") String> ecosystems,
        @Size(max = 10)
        @Convert(converter = DelimitedListConverter.Lines.class)
        @Column(name = "GOLDEN_FIX_VERSION_TYPES", length = 1000)
        List<@Pattern(regexp = "^[a-z-]{1,100}$", message = "must be a Nexus IQ remediation type such as recommended-non-breaking")
                String> goldenVersionTypes,
        @Size(max = 30)
        @Convert(converter = DelimitedListConverter.Lines.class)
        @Column(name = "GOLDEN_FIX_EXCLUDE_DIRS", length = 2000)
        List<@Size(min = 1, max = 200) String> excludeDirs,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "GOLDEN_FIX_VERIFY")
        Boolean verifyEnabled,
        @Min(1) @Max(10)
        @Column(name = "GOLDEN_FIX_VERIFY_ATTEMPTS")
        Integer verifyMaxAttempts,
        @Min(1) @Max(240)
        @Column(name = "GOLDEN_FIX_VERIFY_TIMEOUT")
        Integer verifyTimeoutMinutes,
        @Size(max = 500) @Column(name = "GOLDEN_FIX_VERIFY_MAVEN", length = 500) String verifyMavenCommand,
        @Size(max = 500) @Column(name = "GOLDEN_FIX_VERIFY_GRADLE", length = 500) String verifyGradleCommand,
        @Size(max = 500) @Column(name = "GOLDEN_FIX_VERIFY_NPM", length = 500) String verifyNpmCommand,
        @Size(max = 500) @Column(name = "GOLDEN_FIX_VERIFY_PIP", length = 500) String verifyPipCommand,
        @Size(max = 500) @Column(name = "GOLDEN_FIX_VERIFY_PUB", length = 500) String verifyPubCommand,
        @Size(max = 200)
        @Column(name = "GOLDEN_FIX_AUTHOR_NAME", length = 200)
        String commitAuthorName,
        @Email @Size(max = 320)
        @Column(name = "GOLDEN_FIX_AUTHOR_EMAIL", length = 320)
        String commitAuthorEmail,
        @Size(max = 100)
        @Pattern(regexp = "^[A-Za-z0-9_+/-]*$", message = "must be a time zone ID such as Europe/Warsaw or UTC")
        @Column(name = "GOLDEN_FIX_TIME_ZONE", length = 100)
        String timeZone) {

    public static final GoldenFixPolicy INHERITED = inherit(true);

    public GoldenFixPolicy {
        enabled = !Boolean.FALSE.equals(enabled);
        ecosystems = DelimitedListConverter.clean(ecosystems);
        goldenVersionTypes = DelimitedListConverter.clean(goldenVersionTypes);
        excludeDirs = DelimitedListConverter.clean(excludeDirs);
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
