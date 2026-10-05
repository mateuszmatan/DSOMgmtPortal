package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.catalog.NexusIqSettings;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import org.hibernate.type.NumericBooleanConverter;

import java.util.List;

@Embeddable
public record NexusIqSettingsEmbeddable(
        @Column(name = "NEXUS_IQ_APPLICATION", length = 200) String application,
        @Convert(converter = DelimitedListConverter.Lines.class)
        @Column(name = "NEXUS_IQ_SCAN_PATTERNS", length = 2000) List<String> scanPatterns,
        @Column(name = "NEXUS_IQ_STAGE", nullable = false, length = 50) String stage,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "NEXUS_IQ_FAIL_ON_NETWORK_ERROR", nullable = false) Boolean failOnNetworkError,
        @Column(name = "SCA_SCAN_NAME", length = 200) String scaScanName) {

    static NexusIqSettingsEmbeddable of(NexusIqSettings nexusIq) {
        return new NexusIqSettingsEmbeddable(nexusIq.application(), nexusIq.scanPatterns(), nexusIq.stage(),
                nexusIq.failOnNetworkError(), nexusIq.scaScanName());
    }

    NexusIqSettings toDomain() {
        return new NexusIqSettings(application, scanPatterns, stage, failOnNetworkError, scaScanName);
    }
}
