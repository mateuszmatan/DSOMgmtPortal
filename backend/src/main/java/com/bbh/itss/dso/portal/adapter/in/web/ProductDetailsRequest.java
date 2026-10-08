package com.bbh.itss.dso.portal.adapter.in.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductDetailsRequest(@NotBlank @Size(max = 200) String name, Long departmentId,
                                    @Size(max = 200) String ownerTeam, @Email @Size(max = 320) String contactEmail,
                                    Long version) {
}
