package com.bbh.itss.dso.portal.domain.evidence;

import com.bbh.itss.dso.portal.domain.catalog.TestStage;

public record TestSuiteEvidence(TestStage stage, CheckStatus status, Long jobs, Long passed, Long failed,
                                Long notConfigured, Long durationMs) {
}
