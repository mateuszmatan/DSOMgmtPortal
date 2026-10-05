package com.bbh.itss.dso.portal.domain.evidence;

import java.util.List;

public record RunEvidenceReport(BuildEvidence build, CoverageEvidence coverage, List<TestSuiteEvidence> testSuites,
                                List<ScanEvidence> scans, ReleaseGateEvidence releaseGate, List<StageEvidence> stages) {

    public RunEvidenceReport {
        testSuites = List.copyOf(testSuites);
        scans = List.copyOf(scans);
        stages = List.copyOf(stages);
    }
}
