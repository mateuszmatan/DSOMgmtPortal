package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.catalog.port.in.ProductCommand;
import com.bbh.itss.dso.portal.domain.catalog.ProductDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_-]{1,49}$",
                message = "use 2 to 50 upper case letters, digits, '-' or '_', starting with a letter")
        String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 4000) String description,
        @Size(max = 200) String ownerTeam,
        @Email @Size(max = 320) String contactEmail,
        @NotNull @Valid AppScanAccountDto appScan,
        Long version,
        @NotNull List<@NotNull @Valid ServiceRequest> services) {

    ProductCommand toCommand() {
        return new ProductCommand(version, new ProductDetails(code, name, description, ownerTeam, contactEmail),
                appScan.toDomain(), services.stream().map(ServiceRequest::toCommand).toList());
    }
}
