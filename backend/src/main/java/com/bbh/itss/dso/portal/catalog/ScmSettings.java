package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.type.NumericBooleanConverter;

/**
 * The Bitbucket repository GoldenFix raises dependency upgrade pull requests against ({@code scm.bitbucket})
 * and whether GoldenFix runs at all ({@code goldenFix.enabled}).
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
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "GOLDEN_FIX_ENABLED", nullable = false)
        Boolean goldenFixEnabled) implements ConfigSection {

    public static final ScmSettings NONE = new ScmSettings(null, null, true);

    /** GoldenFix is on unless switched off, as in the library defaults. */
    public ScmSettings {
        repositoryUrl = Text.trimToNull(repositoryUrl);
        credentialsId = Text.trimToNull(credentialsId);
        goldenFixEnabled = !Boolean.FALSE.equals(goldenFixEnabled);
    }

    @Override
    public void writeTo(ConfigTree config) {
        config.set("scm.bitbucket.url", repositoryUrl)
                .set("scm.bitbucket.credentialsId", credentialsId)
                .set("goldenFix.enabled", goldenFixEnabled);
    }
}
