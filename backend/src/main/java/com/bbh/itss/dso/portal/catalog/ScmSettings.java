package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.DelimitedListConverter;
import com.bbh.itss.dso.portal.common.Text;
import com.bbh.itss.dso.portal.common.ValidationProblems;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The Bitbucket repository GoldenFix raises dependency upgrade pull requests against ({@code scm.bitbucket}).
 */
@Embeddable
public record ScmSettings(
        @Size(max = 1000)
        @Pattern(regexp = "^(https?://\\S+)?$", message = "must be an http or https URL")
        @Column(name = "REPOSITORY_URL", length = 1000)
        String repositoryUrl,
        @Size(max = 200)
        @Column(name = "BITBUCKET_CREDENTIALS_ID", length = 200)
        String credentialsId,
        @Enumerated(EnumType.STRING)
        @Column(name = "BITBUCKET_AUTH_TYPE", nullable = false, length = 20)
        BitbucketAuthType authType,
        @Enumerated(EnumType.STRING)
        @Column(name = "BITBUCKET_TYPE", length = 20)
        BitbucketType type,
        @Size(max = 200)
        @Column(name = "BITBUCKET_TARGET_BRANCH", length = 200)
        String targetBranch,
        @Size(max = 1000)
        @Pattern(regexp = "^(https?://\\S+|ssh://\\S+)?$", message = "must be an http, https or ssh URL")
        @Column(name = "BITBUCKET_CLONE_URL", length = 1000)
        String cloneUrl,
        @Size(max = 20)
        @Convert(converter = DelimitedListConverter.Commas.class)
        @Column(name = "BITBUCKET_REVIEWERS", length = 2000)
        List<@Pattern(regexp = "^[^,\\s]{1,100}$", message = "one Bitbucket user name or account UUID per entry")
                String> reviewers) implements ConfigSection {

    public static final ScmSettings NONE = new ScmSettings(null, null, BitbucketAuthType.BASIC, null, null, null, List.of());

    public ScmSettings {
        repositoryUrl = Text.trimToNull(repositoryUrl);
        credentialsId = Text.trimToNull(credentialsId);
        authType = authType == null ? BitbucketAuthType.BASIC : authType;
        targetBranch = Text.trimToNull(targetBranch);
        cloneUrl = Text.trimToNull(cloneUrl);
        reviewers = DelimitedListConverter.clean(reviewers);
    }

    /** A repository and its credentials with every other option at its default. */
    public static ScmSettings of(String repositoryUrl, String credentialsId) {
        return new ScmSettings(repositoryUrl, credentialsId, BitbucketAuthType.BASIC, null, null, null, List.of());
    }

    /** Nothing is written without a repository: GoldenFix then lists the fixes in the report only. */
    @Override
    public void writeTo(ConfigTree config) {
        if (repositoryUrl == null) {
            return;
        }
        config.set("scm.bitbucket.url", repositoryUrl)
                .set("scm.bitbucket.credentialsId", credentialsId)
                .set("scm.bitbucket.authType", authType.configValue())
                .set("scm.bitbucket.type", type == null ? null : type.configValue())
                .set("scm.bitbucket.targetBranch", targetBranch)
                .set("scm.bitbucket.cloneUrl", cloneUrl)
                .set("scm.bitbucket.reviewers", reviewers);
    }

    /** GoldenFix pushes its branch and opens the pull request with these credentials. */
    @Override
    public void validate(ValidationProblems problems) {
        if (repositoryUrl != null && credentialsId == null) {
            problems.add("credentialsId", "is required to push GoldenFix branches and open pull requests");
        }
    }
}
