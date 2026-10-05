package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigSection;
import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.List;

public record ScmSettings(
        String repositoryUrl,
        String credentialsId,
        BitbucketAuthType authType,
        BitbucketType type,
        String targetBranch,
        String cloneUrl,
        List<String> reviewers) implements ConfigSection {

    public static final ScmSettings NONE = new ScmSettings(null, null, BitbucketAuthType.BASIC, null, null, null, List.of());

    public ScmSettings {
        repositoryUrl = Text.trimToNull(repositoryUrl);
        credentialsId = Text.trimToNull(credentialsId);
        authType = authType == null ? BitbucketAuthType.BASIC : authType;
        targetBranch = Text.trimToNull(targetBranch);
        cloneUrl = Text.trimToNull(cloneUrl);
        reviewers = Text.clean(reviewers);
    }

    public static ScmSettings of(String repositoryUrl, String credentialsId) {
        return new ScmSettings(repositoryUrl, credentialsId, BitbucketAuthType.BASIC, null, null, null, List.of());
    }

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

    @Override
    public void validate(ValidationProblems problems) {
        if (repositoryUrl != null && credentialsId == null) {
            problems.add("credentialsId", "is required to push GoldenFix branches and open pull requests");
        }
    }
}
