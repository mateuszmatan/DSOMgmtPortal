package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.catalog.GoldenFixPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import org.hibernate.type.NumericBooleanConverter;

import java.util.List;

@Embeddable
public record GoldenFixPolicyEmbeddable(
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "GOLDEN_FIX_ENABLED", nullable = false) Boolean enabled,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "GOLDEN_FIX_DIRECT_ONLY") Boolean onlyDirectDependencies,
        @Column(name = "GOLDEN_FIX_MIN_THREAT_LEVEL") Integer minThreatLevel,
        @Convert(converter = DelimitedListConverter.Commas.class)
        @Column(name = "GOLDEN_FIX_ECOSYSTEMS", length = 200) List<String> ecosystems,
        @Convert(converter = DelimitedListConverter.Lines.class)
        @Column(name = "GOLDEN_FIX_VERSION_TYPES", length = 1000) List<String> goldenVersionTypes,
        @Convert(converter = DelimitedListConverter.Lines.class)
        @Column(name = "GOLDEN_FIX_EXCLUDE_DIRS", length = 2000) List<String> excludeDirs,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "GOLDEN_FIX_VERIFY") Boolean verifyEnabled,
        @Column(name = "GOLDEN_FIX_VERIFY_ATTEMPTS") Integer verifyMaxAttempts,
        @Column(name = "GOLDEN_FIX_VERIFY_TIMEOUT") Integer verifyTimeoutMinutes,
        @Column(name = "GOLDEN_FIX_VERIFY_MAVEN", length = 500) String verifyMavenCommand,
        @Column(name = "GOLDEN_FIX_VERIFY_GRADLE", length = 500) String verifyGradleCommand,
        @Column(name = "GOLDEN_FIX_VERIFY_NPM", length = 500) String verifyNpmCommand,
        @Column(name = "GOLDEN_FIX_VERIFY_PIP", length = 500) String verifyPipCommand,
        @Column(name = "GOLDEN_FIX_VERIFY_PUB", length = 500) String verifyPubCommand,
        @Column(name = "GOLDEN_FIX_AUTHOR_NAME", length = 200) String commitAuthorName,
        @Column(name = "GOLDEN_FIX_AUTHOR_EMAIL", length = 320) String commitAuthorEmail,
        @Column(name = "GOLDEN_FIX_TIME_ZONE", length = 100) String timeZone) {

    public static GoldenFixPolicyEmbeddable of(GoldenFixPolicy policy) {
        return new GoldenFixPolicyEmbeddable(policy.enabled(), policy.onlyDirectDependencies(), policy.minThreatLevel(),
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
