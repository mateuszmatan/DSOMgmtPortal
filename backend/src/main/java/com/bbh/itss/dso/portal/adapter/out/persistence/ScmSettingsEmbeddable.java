package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.catalog.BitbucketAuthType;
import com.bbh.itss.dso.portal.domain.catalog.BitbucketType;
import com.bbh.itss.dso.portal.domain.catalog.ScmSettings;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.List;

@Embeddable
public record ScmSettingsEmbeddable(
        @Column(name = "REPOSITORY_URL", length = 1000) String repositoryUrl,
        @Column(name = "BITBUCKET_CREDENTIALS_ID", length = 200) String credentialsId,
        @Enumerated(EnumType.STRING)
        @Column(name = "BITBUCKET_AUTH_TYPE", nullable = false, length = 20) BitbucketAuthType authType,
        @Enumerated(EnumType.STRING)
        @Column(name = "BITBUCKET_TYPE", length = 20) BitbucketType type,
        @Column(name = "BITBUCKET_TARGET_BRANCH", length = 200) String targetBranch,
        @Column(name = "BITBUCKET_CLONE_URL", length = 1000) String cloneUrl,
        @Convert(converter = DelimitedListConverter.Commas.class)
        @Column(name = "BITBUCKET_REVIEWERS", length = 2000) List<String> reviewers) {

    static ScmSettingsEmbeddable of(ScmSettings scm) {
        return new ScmSettingsEmbeddable(scm.repositoryUrl(), scm.credentialsId(), scm.authType(), scm.type(),
                scm.targetBranch(), scm.cloneUrl(), scm.reviewers());
    }

    ScmSettings toDomain() {
        return new ScmSettings(repositoryUrl, credentialsId, authType, type, targetBranch, cloneUrl, reviewers);
    }
}
