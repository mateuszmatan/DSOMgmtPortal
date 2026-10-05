package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.settings.GlobalSettings;
import com.bbh.itss.dso.portal.domain.settings.GlobalSettingsValues;
import com.bbh.itss.dso.portal.domain.settings.Scanner;
import com.bbh.itss.dso.portal.domain.settings.SeverityLimits;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
class GlobalSettingsMapper {

    GlobalSettings toDomain(GlobalSettingsEntity entity) {
        Map<Scanner, SeverityLimits> limits = new EnumMap<>(Scanner.class);
        entity.limits().forEach((scanner, value) -> limits.put(scanner, value.toDomain()));
        GlobalSettingsValues values = new GlobalSettingsValues(entity.platform().toDomain(),
                entity.deployment().toDomain(), limits, entity.scans().toDomain(), entity.releaseGate().toDomain(),
                entity.serviceDefaults().toDomain(), entity.goldenFix().toDomain());
        return new GlobalSettings(values, entity.getVersion(), entity.getUpdatedAt());
    }

    void copy(GlobalSettingsValues values, GlobalSettingsEntity entity) {
        Map<Scanner, SeverityLimitsEmbeddable> limits = new EnumMap<>(Scanner.class);
        values.limits().forEach((scanner, value) -> limits.put(scanner, SeverityLimitsEmbeddable.of(value)));
        entity.platform(PlatformSettingsEmbeddable.of(values.platform()));
        entity.deployment(DeploymentDefaultsEmbeddable.of(values.deployment()));
        entity.limits(limits);
        entity.scans(ScanSettingsEmbeddable.of(values.scans()));
        entity.releaseGate(ReleaseGateSettingsEmbeddable.of(values.releaseGate()));
        entity.serviceDefaults(ServiceDefaultsEmbeddable.of(values.serviceDefaults()));
        entity.goldenFix(GoldenFixPolicyEmbeddable.of(values.goldenFix()));
    }
}
