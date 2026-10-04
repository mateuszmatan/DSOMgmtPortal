package com.bbh.dso.portal.catalog;

import com.bbh.dso.portal.common.DelimitedListConverter;
import com.bbh.dso.portal.common.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The service's Nexus IQ application and the Ant patterns of the artifacts it scans ({@code tools.nexusIq}).
 */
@Embeddable
public record NexusIqSettings(
        @Size(max = 200)
        @Column(name = "NEXUS_IQ_APPLICATION", length = 200)
        String application,
        @Size(max = 20)
        @Convert(converter = DelimitedListConverter.Lines.class)
        @Column(name = "NEXUS_IQ_SCAN_PATTERNS", length = 2000)
        List<@NotBlank @Size(max = 300) String> scanPatterns) implements ConfigSection {

    public static final NexusIqSettings NONE = new NexusIqSettings(null, List.of());

    public NexusIqSettings {
        application = Text.trimToNull(application);
        scanPatterns = DelimitedListConverter.clean(scanPatterns);
    }

    @Override
    public void writeTo(ConfigTree config) {
        config.set("tools.nexusIq.application", application).set("tools.nexusIq.scanPatterns", scanPatterns);
    }
}
