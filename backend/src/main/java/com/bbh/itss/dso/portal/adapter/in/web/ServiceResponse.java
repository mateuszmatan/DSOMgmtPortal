package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.Region;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.catalog.ServiceSettings;

import java.util.List;
import java.util.Map;

import static com.bbh.itss.dso.portal.adapter.in.web.DtoMapping.list;
import static com.bbh.itss.dso.portal.adapter.in.web.DtoMapping.regions;

public record ServiceResponse(
        Long id,
        String name,
        String description,
        BuildSettingsDto build,
        UnitTestSettingsDto unitTests,
        TestSettingsDto tests,
        List<TestJobDto> testJobs,
        DeploymentSettingsDto deployment,
        ToolCommandDto delivery,
        UrbanCodeSettingsDto urbanCode,
        List<UrbanCodeApplicationSettingsDto> urbanCodeApplications,
        Map<Region, SshTargetDto> sshTargets,
        Map<Region, OpenShiftTargetDto> openShiftTargets,
        AppScanSettingsDto appScan,
        SonarSettingsDto sonar,
        NexusIqSettingsDto nexusIq,
        ScmSettingsDto scm,
        GoldenFixPolicyDto goldenFix,
        MetricsSettingsDto metrics,
        FlutterSettingsDto flutter) {

    static ServiceResponse from(Service service) {
        ServiceSettings settings = service.settings();
        return new ServiceResponse(service.id(), service.name(), service.description(),
                BuildSettingsDto.from(settings.build()), UnitTestSettingsDto.from(settings.unitTests()),
                TestSettingsDto.from(settings.tests()), list(settings.testJobs(), TestJobDto::from),
                DeploymentSettingsDto.from(settings.deployment()), ToolCommandDto.from(settings.delivery()),
                UrbanCodeSettingsDto.from(settings.urbanCode()),
                list(settings.urbanCodeApplications(), UrbanCodeApplicationSettingsDto::from),
                regions(settings.sshTargets(), SshTargetDto::from),
                regions(settings.openShiftTargets(), OpenShiftTargetDto::from),
                AppScanSettingsDto.from(settings.appScan()), SonarSettingsDto.from(settings.sonar()),
                NexusIqSettingsDto.from(settings.nexusIq()), ScmSettingsDto.from(settings.scm()),
                GoldenFixPolicyDto.from(settings.goldenFix()), MetricsSettingsDto.from(settings.metrics()),
                FlutterSettingsDto.from(settings.flutter()));
    }
}
