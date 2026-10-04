package com.bbh.itss.dso.portal.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A product with the complete list of its services. On update the list replaces the stored one: services
 * with an id are changed, services without one are added and stored services missing from the list are
 * removed together with their pipelines.
 *
 * @param version the version the client edited, to detect a concurrent change; null skips the check
 */
public record ProductRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_-]{1,49}$",
                message = "use 2 to 50 upper case letters, digits, '-' or '_', starting with a letter")
        String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 4000) String description,
        @Size(max = 200) String ownerTeam,
        @Email @Size(max = 320) String contactEmail,
        @NotNull @Valid AppScanAccount appScan,
        Long version,
        @NotNull List<@NotNull @Valid ServiceRequest> services) {

    ProductDetails details() {
        return new ProductDetails(code, name, description, ownerTeam, contactEmail);
    }
}
