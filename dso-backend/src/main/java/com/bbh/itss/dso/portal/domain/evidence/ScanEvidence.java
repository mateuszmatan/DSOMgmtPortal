package com.bbh.itss.dso.portal.domain.evidence;

import lombok.Builder;

@Builder
public record ScanEvidence(EvidenceScanner scanner, CheckStatus status, Long critical, Long high, Long medium,
                           Long low, Long maxCritical, Long maxHigh, Long maxMedium, String qualityGate, String link) {
}
