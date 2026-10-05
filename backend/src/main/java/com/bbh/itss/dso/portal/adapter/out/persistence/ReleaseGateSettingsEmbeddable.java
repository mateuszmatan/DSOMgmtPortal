package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.settings.ReleaseGateSettings;
import com.bbh.itss.dso.portal.domain.settings.Scanner;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import org.hibernate.type.NumericBooleanConverter;

import java.util.List;

@Embeddable
public record ReleaseGateSettingsEmbeddable(
        @Convert(converter = ScannerListConverter.class)
        @Column(name = "RELEASE_GATE_SCANNERS", length = 100) List<Scanner> scanners,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "RELEASE_GATE_REQUIRE_COVERAGE", nullable = false) Boolean requireCoverage,
        @Column(name = "RELEASE_GATE_STATE_FILE", nullable = false, length = 200) String stateFile) {

    static ReleaseGateSettingsEmbeddable of(ReleaseGateSettings releaseGate) {
        return new ReleaseGateSettingsEmbeddable(releaseGate.scanners(), releaseGate.requireCoverage(),
                releaseGate.stateFile());
    }

    ReleaseGateSettings toDomain() {
        return new ReleaseGateSettings(scanners, requireCoverage, stateFile);
    }
}
