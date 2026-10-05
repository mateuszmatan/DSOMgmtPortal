package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.catalog.port.in.ServiceCommand;
import com.bbh.itss.dso.portal.domain.catalog.Region;
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

import static com.bbh.itss.dso.portal.adapter.in.web.DtoMapping.list;
import static com.bbh.itss.dso.portal.adapter.in.web.DtoMapping.mapped;
import static com.bbh.itss.dso.portal.adapter.in.web.DtoMapping.regions;

public record ServiceRequest(
        Long id,
        @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9._-]{0,99}$",
                message = "use lower case letters, digits, '.', '-' or '_', starting with a letter or digit")
        String name,
        @Size(max = 2000) String description,
        @NotNull @Valid BuildSettingsDto build,
        @Valid UnitTestSettingsDto unitTests,
        @Valid TestSettingsDto tests,
        @Size(max = 100) List<@NotNull @Valid TestJobDto> testJobs,
        @NotNull @Valid DeploymentSettingsDto deployment,
        @Valid ToolCommandDto delivery,
        @Valid UrbanCodeSettingsDto urbanCode,
        @Size(max = 20) List<@NotNull @Valid UrbanCodeApplicationSettingsDto> urbanCodeApplications,
        Map<Region, @Valid SshTargetDto> sshTargets,
        Map<Region, @Valid OpenShiftTargetDto> openShiftTargets,
        @NotNull @Valid AppScanSettingsDto appScan,
        @Valid SonarSettingsDto sonar,
        @Valid NexusIqSettingsDto nexusIq,
        @Valid ScmSettingsDto scm,
        @Valid GoldenFixPolicyDto goldenFix,
        @Valid MetricsSettingsDto metrics,
        @Valid FlutterSettingsDto flutter) {

    ServiceCommand toCommand() {
        return new ServiceCommand(id, name, description, settings());
    }

    ServiceSettings settings() {
        return new ServiceSettings(build.toDomain(), mapped(unitTests, UnitTestSettingsDto::toDomain),
                mapped(tests, TestSettingsDto::toDomain), list(testJobs, TestJobDto::toDomain), deployment.toDomain(),
                mapped(delivery, ToolCommandDto::toDomain), mapped(urbanCode, UrbanCodeSettingsDto::toDomain),
                list(urbanCodeApplications, UrbanCodeApplicationSettingsDto::toDomain),
                regions(sshTargets, SshTargetDto::toDomain), regions(openShiftTargets, OpenShiftTargetDto::toDomain),
                appScan.toDomain(), mapped(sonar, SonarSettingsDto::toDomain),
                mapped(nexusIq, NexusIqSettingsDto::toDomain), mapped(scm, ScmSettingsDto::toDomain),
                mapped(goldenFix, GoldenFixPolicyDto::toDomain), mapped(metrics, MetricsSettingsDto::toDomain),
                mapped(flutter, FlutterSettingsDto::toDomain));
    }
}
