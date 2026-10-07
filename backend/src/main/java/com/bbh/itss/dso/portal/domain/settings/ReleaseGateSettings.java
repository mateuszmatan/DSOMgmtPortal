package com.bbh.itss.dso.portal.domain.settings;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.Comparator;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.trimToNull;

public record ReleaseGateSettings(List<Scanner> scanners, Boolean requireCoverage, String stateFile) {

    public ReleaseGateSettings {
        scanners = normalize(scanners);
        stateFile = trimToNull(stateFile);
    }

    public static List<Scanner> normalize(List<Scanner> scanners) {
        return scanners == null ? List.of()
                : scanners.stream().distinct().sorted(Comparator.nullsLast(Comparator.naturalOrder())).toList();
    }

    public void validate(ValidationProblems problems) {
        problems.require("scanners", scanners,
                "select at least one scanner: without any the library gates on all four");
    }

    public void writeTo(ConfigTree defaults) {
        defaults.set("releaseGate.scanners", scanners.stream().map(Scanner::gateKey).toList())
                .set("releaseGate.requireCoverage", requireCoverage)
                .set("releaseGate.stateFile", stateFile);
    }
}
