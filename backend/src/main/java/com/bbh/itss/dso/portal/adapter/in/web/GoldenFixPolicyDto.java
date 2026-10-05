package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GoldenFixPolicyDto(
        Boolean enabled,
        Boolean onlyDirectDependencies,
        @Min(1) @Max(10)
        Integer minThreatLevel,
        @Size(max = 4)
        List<@Pattern(regexp = "^(maven|npm|pypi|pub)$", message = "must be maven, npm, pypi or pub") String> ecosystems,
        @Size(max = 10)
        List<@Pattern(regexp = "^[a-z-]{1,100}$", message = "must be a Nexus IQ remediation type such as recommended-non-breaking")
                String> goldenVersionTypes,
        @Size(max = 30)
        List<@Size(min = 1, max = 200) String> excludeDirs,
        Boolean verifyEnabled,
        @Min(1) @Max(10)
        Integer verifyMaxAttempts,
        @Min(1) @Max(240)
        Integer verifyTimeoutMinutes,
        @Size(max = 500) String verifyMavenCommand,
        @Size(max = 500) String verifyGradleCommand,
        @Size(max = 500) String verifyNpmCommand,
        @Size(max = 500) String verifyPipCommand,
        @Size(max = 500) String verifyPubCommand,
        @Size(max = 200)
        String commitAuthorName,
        @Email @Size(max = 320)
        String commitAuthorEmail,
        @Size(max = 100)
        @Pattern(regexp = "^[A-Za-z0-9_+/-]*$", message = "must be a time zone ID such as Europe/Warsaw or UTC")
        String timeZone) {

    public GoldenFixPolicyDto {
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

    public static GoldenFixPolicyDto from(GoldenFixPolicy policy) {
        return new GoldenFixPolicyDto(policy.enabled(), policy.onlyDirectDependencies(), policy.minThreatLevel(),
                policy.ecosystems(), policy.goldenVersionTypes(), policy.excludeDirs(), policy.verifyEnabled(),
                policy.verifyMaxAttempts(), policy.verifyTimeoutMinutes(), policy.verifyMavenCommand(),
                policy.verifyGradleCommand(), policy.verifyNpmCommand(), policy.verifyPipCommand(),
                policy.verifyPubCommand(), policy.commitAuthorName(), policy.commitAuthorEmail(), policy.timeZone());
    }

    public GoldenFixPolicy toDomain() {
        return new GoldenFixPolicy(enabled, onlyDirectDependencies, minThreatLevel, ecosystems, goldenVersionTypes,
                excludeDirs, verifyEnabled, verifyMaxAttempts, verifyTimeoutMinutes, verifyMavenCommand,
                verifyGradleCommand, verifyNpmCommand, verifyPipCommand, verifyPubCommand, commitAuthorName,
                commitAuthorEmail, timeZone);
    }
}
