package com.bbh.itss.dso.portal.domain.evidence;

public record StageEvidence(String name, CheckStatus status, Long durationSeconds, String reason) {
}
