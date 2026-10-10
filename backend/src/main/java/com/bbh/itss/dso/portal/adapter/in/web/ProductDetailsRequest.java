package com.bbh.itss.dso.portal.adapter.in.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static com.bbh.itss.dso.portal.domain.catalog.ProductDetails.NAME_MAX;
import static com.bbh.itss.dso.portal.domain.catalog.ProductDetails.OWNER_TEAM_MAX;

public record ProductDetailsRequest(@NotBlank @Size(max = NAME_MAX) String name, Long departmentId,
                                    @Size(max = OWNER_TEAM_MAX) String ownerTeam,
                                    @Email @Size(max = 320) String contactEmail, Long version) {
}
