package com.bbh.itss.dso.portal.domain.evidence;

public record ReleaseGateEvidence(boolean allowed, Long violations, String reason) {
}
