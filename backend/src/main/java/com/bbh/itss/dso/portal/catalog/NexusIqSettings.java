package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.DelimitedListConverter;
import com.bbh.itss.dso.portal.common.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.type.NumericBooleanConverter;

import java.util.List;

/**
 * The service's Nexus IQ application ({@code tools.nexusIq}): the Ant patterns of the artifacts it scans, the
 * Nexus IQ stage, whether a network error fails the build, and the name of the SCA findings in the report
 * ({@code sca.scanName}). The server and its credentials are global settings.
 */
@Embeddable
public record NexusIqSettings(
        @Size(max = 200)
        @Column(name = "NEXUS_IQ_APPLICATION", length = 200)
        String application,
        @Size(max = 20)
        @Convert(converter = DelimitedListConverter.Lines.class)
        @Column(name = "NEXUS_IQ_SCAN_PATTERNS", length = 2000)
        List<@NotBlank @Size(max = 300) String> scanPatterns,
        @Size(max = 50)
        @Pattern(regexp = "^[a-z-]*$", message = "must be a Nexus IQ stage such as build, stage-release or release")
        @Column(name = "NEXUS_IQ_STAGE", nullable = false, length = 50)
        String stage,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "NEXUS_IQ_FAIL_ON_NETWORK_ERROR", nullable = false)
        Boolean failOnNetworkError,
        @Size(max = 200)
        @Column(name = "SCA_SCAN_NAME", length = 200)
        String scaScanName) implements ConfigSection {

    public static final String DEFAULT_STAGE = "build";
    public static final NexusIqSettings NONE = new NexusIqSettings(null, List.of(), null, false, null);

    public NexusIqSettings {
        application = Text.trimToNull(application);
        scanPatterns = DelimitedListConverter.clean(scanPatterns);
        stage = Text.orDefault(stage, DEFAULT_STAGE);
        failOnNetworkError = Boolean.TRUE.equals(failOnNetworkError);
        scaScanName = Text.trimToNull(scaScanName);
    }

    /** An application and its scan patterns with every other option at its default. */
    public static NexusIqSettings of(String application, List<String> scanPatterns) {
        return new NexusIqSettings(application, scanPatterns, null, false, null);
    }

    @Override
    public void writeTo(ConfigTree config) {
        config.set("tools.nexusIq.application", application)
                .set("tools.nexusIq.scanPatterns", scanPatterns)
                .set("tools.nexusIq.stage", stage)
                .set("tools.nexusIq.failOnNetworkError", failOnNetworkError)
                .set("sca.scanName", scaScanName);
    }
}
