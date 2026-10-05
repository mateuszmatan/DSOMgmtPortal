package com.bbh.itss.dso.portal.domain.evidence;

public record CoverageEvidence(CheckStatus status, Double linePercent, Double requiredPercent, Long coveredLines,
                               Long totalLines) {
}
