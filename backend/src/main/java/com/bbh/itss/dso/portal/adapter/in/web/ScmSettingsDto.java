package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.BitbucketAuthType;
import com.bbh.itss.dso.portal.domain.catalog.BitbucketType;
import com.bbh.itss.dso.portal.domain.catalog.ScmSettings;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.URL;
import static com.bbh.itss.dso.portal.adapter.in.web.InputFormats.URL_MESSAGE;

public record ScmSettingsDto(
        @Size(max = 1000)
        @Pattern(regexp = URL, message = URL_MESSAGE)
        String repositoryUrl,
        @Size(max = 200)
        String credentialsId,
        BitbucketAuthType authType,
        BitbucketType type,
        @Size(max = 200)
        String targetBranch,
        @Size(max = 1000)
        @Pattern(regexp = "^(https?://\\S+|ssh://\\S+)?$", message = "must be an http, https or ssh URL")
        String cloneUrl,
        @Size(max = 20)
        List<@Pattern(regexp = "^[^,\\s]{1,100}$", message = "one Bitbucket user name or account UUID per entry")
                String> reviewers) {

    public ScmSettingsDto {
        repositoryUrl = Text.trimToNull(repositoryUrl);
        credentialsId = Text.trimToNull(credentialsId);
        authType = authType == null ? BitbucketAuthType.BASIC : authType;
        targetBranch = Text.trimToNull(targetBranch);
        cloneUrl = Text.trimToNull(cloneUrl);
        reviewers = Text.clean(reviewers);
    }

    static ScmSettingsDto from(ScmSettings source) {
        return new ScmSettingsDto(source.repositoryUrl(), source.credentialsId(), source.authType(), source.type(),
                source.targetBranch(), source.cloneUrl(), source.reviewers());
    }

    ScmSettings toDomain() {
        return new ScmSettings(repositoryUrl, credentialsId, authType, type, targetBranch, cloneUrl, reviewers);
    }
}
