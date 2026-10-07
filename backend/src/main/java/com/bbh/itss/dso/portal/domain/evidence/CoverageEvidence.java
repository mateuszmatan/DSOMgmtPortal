package com.bbh.itss.dso.portal.domain.evidence;

import lombok.Builder;

@Builder
public record CoverageEvidence(CheckStatus status, Double linePercent, Double requiredPercent, Long coveredLines,
                               Long totalLines) {
}
