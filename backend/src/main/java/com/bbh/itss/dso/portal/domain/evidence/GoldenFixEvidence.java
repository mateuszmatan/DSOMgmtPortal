package com.bbh.itss.dso.portal.domain.evidence;

public record GoldenFixEvidence(String status, int offered, int applied, int unresolved, boolean pullRequestRaised,
                                String pullRequestUrl, String pullRequestTitle) {
}
