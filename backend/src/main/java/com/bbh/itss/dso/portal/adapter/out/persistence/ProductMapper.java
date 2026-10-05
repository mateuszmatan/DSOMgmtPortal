package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.ProductDetails;
import com.bbh.itss.dso.portal.domain.catalog.Region;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

@Component
class ProductMapper {

    Product toDomain(ProductEntity entity) {
        ProductDetails details = new ProductDetails(entity.code(), entity.name(), entity.description(),
                entity.ownerTeam(), entity.contactEmail());
        return Product.restore(entity.getId(), details, entity.appScanAccount().toDomain(),
                entity.services().stream().map(this::toDomain).toList(), entity.getVersion(), entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    Service toDomain(ServiceEntity entity) {
        ServiceSettings settings = new ServiceSettings(
                entity.build().toDomain(),
                UnitTestSettingsEmbeddable.toDomain(entity.unitTests()),
                TestSettingsEmbeddable.toDomain(entity.tests()),
                entity.testJobs().stream().map(TestJobEmbeddable::toDomain).toList(),
                entity.deployment().toDomain(),
                ToolCommandEmbeddable.toDomain(entity.delivery()),
                entity.urbanCode().toDomain(),
                entity.urbanCodeApplications(),
                values(entity.sshTargets(), SshTargetEmbeddable::toDomain),
                values(entity.openShiftTargets(), OpenShiftTargetEmbeddable::toDomain),
                entity.appScan().toDomain(),
                entity.sonar().toDomain(),
                entity.nexusIq().toDomain(),
                entity.scm().toDomain(),
                mapped(entity.goldenFix(), GoldenFixPolicyEmbeddable::toDomain),
                entity.metrics().toDomain(),
                entity.flutter().toDomain());
        return new Service(entity.getId(), entity.name(), entity.description(), entity.displayOrder(), settings);
    }

    void copy(Product product, ProductEntity entity) {
        ProductDetails details = product.details();
        entity.details(details.code(), details.name(), details.description(), details.ownerTeam(),
                details.contactEmail(), AppScanAccountEmbeddable.of(product.appScanAccount()));
    }

    void copy(Service service, ServiceEntity entity) {
        ServiceSettings settings = service.settings();
        entity.identity(service.name(), service.description(), service.displayOrder());
        entity.build(BuildSettingsEmbeddable.of(settings.build()));
        entity.unitTests(UnitTestSettingsEmbeddable.of(settings.unitTests()));
        entity.tests(TestSettingsEmbeddable.of(settings.tests()));
        entity.testJobs(settings.testJobs().stream().map(TestJobEmbeddable::of).toList());
        entity.deployment(DeploymentSettingsEmbeddable.of(settings.deployment()));
        entity.delivery(ToolCommandEmbeddable.of(settings.delivery()));
        entity.urbanCode(UrbanCodeSettingsEmbeddable.of(settings.urbanCode()));
        entity.urbanCodeApplications(settings.urbanCodeApplications());
        entity.sshTargets(values(settings.sshTargets(), SshTargetEmbeddable::of));
        entity.openShiftTargets(values(settings.openShiftTargets(), OpenShiftTargetEmbeddable::of));
        entity.appScan(AppScanSettingsEmbeddable.of(settings.appScan()));
        entity.sonar(SonarSettingsEmbeddable.of(settings.sonar()));
        entity.nexusIq(NexusIqSettingsEmbeddable.of(settings.nexusIq()));
        entity.scm(ScmSettingsEmbeddable.of(settings.scm()));
        entity.goldenFix(GoldenFixPolicyEmbeddable.of(settings.goldenFix()));
        entity.metrics(MetricsSettingsEmbeddable.of(settings.metrics()));
        entity.flutter(FlutterSettingsEmbeddable.of(settings.flutter()));
    }

    private static <S, T> T mapped(S source, Function<S, T> mapping) {
        return source == null ? null : mapping.apply(source);
    }

    private static <S, T> Map<Region, T> values(Map<Region, S> source, Function<S, T> mapping) {
        Map<Region, T> mapped = new EnumMap<>(Region.class);
        source.forEach((region, value) -> mapped.put(region, mapping.apply(value)));
        return mapped;
    }
}
