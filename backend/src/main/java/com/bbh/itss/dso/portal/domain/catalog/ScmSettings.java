package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigSection;
import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.StoredList;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public record ScmSettings(String repositoryUrl, String credentialsId, BitbucketAuthType authType, BitbucketType type,
                          String targetBranch, String cloneUrl, List<String> reviewers, String apiUrl, String workspace,
                          String projectKey, String repoSlug) implements ConfigSection {

    public static final ScmSettings NONE = of(null, null);

    public ScmSettings {
        repositoryUrl = Text.trimToNull(repositoryUrl);
        credentialsId = Text.trimToNull(credentialsId);
        authType = authType == null ? BitbucketAuthType.BASIC : authType;
        targetBranch = Text.trimToNull(targetBranch);
        cloneUrl = Text.trimToNull(cloneUrl);
        reviewers = Text.clean(reviewers);
        apiUrl = Text.trimToNull(apiUrl);
        workspace = Text.trimToNull(workspace);
        projectKey = Text.trimToNull(projectKey);
        repoSlug = Text.trimToNull(repoSlug);
    }

    public static ScmSettings of(String repositoryUrl, String credentialsId) {
        return new ScmSettings(repositoryUrl, credentialsId, BitbucketAuthType.BASIC, null, null, null, List.of(),
                null, null, null, null);
    }

    @Override
    public void writeTo(ConfigTree config) {
        if (repositoryUrl == null) {
            return;
        }
        config.set("scm.bitbucket.url", repositoryUrl)
                .set("scm.bitbucket.credentialsId", credentialsId)
                .set("scm.bitbucket.authType", authType)
                .set("scm.bitbucket.type", type)
                .set("scm.bitbucket.targetBranch", targetBranch)
                .set("scm.bitbucket.cloneUrl", cloneUrl)
                .set("scm.bitbucket.reviewers", reviewers)
                .set("scm.bitbucket.apiUrl", apiUrl)
                .set("scm.bitbucket.workspace", workspace)
                .set("scm.bitbucket.projectKey", projectKey)
                .set("scm.bitbucket.repoSlug", repoSlug);
    }

    @Override
    public void validate(ValidationProblems problems) {
        if (repositoryUrl != null && credentialsId == null) {
            problems.add("credentialsId", "is required to push GoldenFix branches and open pull requests");
        }
        if (repositoryUrl == null && namesRepository()) {
            problems.add("repositoryUrl",
                    "is required when the Bitbucket API URL, workspace, project key or repository slug is set");
        }
        StoredList.COMMAS_2000.check(problems, "reviewers", reviewers);
    }

    private boolean namesRepository() {
        return Stream.of(apiUrl, workspace, projectKey, repoSlug).anyMatch(Objects::nonNull);
    }
}
