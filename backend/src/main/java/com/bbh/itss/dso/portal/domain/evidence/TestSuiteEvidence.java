package com.bbh.itss.dso.portal.domain.evidence;

public record TestSuiteEvidence(TestSuite suite, CheckStatus status, Long total, Long passed, Long failed,
                                Long skipped, Long notConfigured, Long durationMs) {
}
