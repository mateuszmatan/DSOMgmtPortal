package com.bbh.itss.dso.portal.domain.evidence;

import lombok.Builder;

@Builder
public record TestSuiteEvidence(TestSuite suite, CheckStatus status, Long total, Long passed, Long failed,
                                Long skipped, Long notConfigured, Long durationMs) {
}
