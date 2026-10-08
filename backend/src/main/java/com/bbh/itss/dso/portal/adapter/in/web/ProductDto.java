package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.adapter.Mirrors;
import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductCommand;
import com.bbh.itss.dso.portal.domain.catalog.AppScanAccount;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.ProductDetails;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(value = {"id", "createdAt", "updatedAt"}, allowGetters = true)
public record ProductDto(
        Long id,
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_-]{1,49}$",
                message = "use 2 to 50 upper case letters, digits, '-' or '_', starting with a letter")
        String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 4000) String description,
        @Size(max = 200) String ownerTeam,
        @Email @Size(max = 320) String contactEmail,
        Long departmentId,
        @Valid AppScanAccountDto appScan,
        Long version,
        Instant createdAt,
        Instant updatedAt,
        @NotNull List<@NotNull @Valid ServiceDto> services) {

    static ProductDto from(Product product) {
        return new ProductDto(product.id(), product.code(), product.name(), product.description(),
                product.ownerTeam(), product.contactEmail(), product.departmentId(),
                RecordMapper.map(product.appScanAccount(), AppScanAccountDto.class), product.version(),
                product.createdAt(), product.updatedAt(), product.services().stream().map(ServiceDto::from).toList());
    }

    ProductCommand toCommand(PipelineType pipelineType) {
        return new ProductCommand(version,
                new ProductDetails(code, name, description, ownerTeam, contactEmail, departmentId),
                RecordMapper.map(appScan, AppScanAccount.class), services.stream().map(ServiceDto::toDraft).toList(),
                pipelineType);
    }

    public record AppScanAccountDto(@Size(max = 200) String keyId, @Size(max = 200) String secretCredentialsId)
            implements Mirrors<AppScanAccount> {
    }
}
