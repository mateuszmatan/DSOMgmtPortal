package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform;
import com.bbh.itss.dso.portal.domain.catalog.FlutterSettings;
import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FlutterSettingsDto(
        FlutterPlatform platform,
        @Size(max = 30) List<@Pattern(regexp = "^[A-Za-z0-9._/-]{1,100}$", message = "must be a module folder name") String> modules,
        @Size(max = 30) List<@Pattern(regexp = "^[A-Za-z0-9._/-]{1,100}$", message = "must be a module folder name") String> testModules,
        @Size(max = 30) List<@Pattern(regexp = "^[A-Za-z0-9._/-]{1,100}$", message = "must be a module folder name") String> testSubmodules,
        @Size(max = 30) List<@Pattern(regexp = "^[A-Za-z0-9._/-]{1,100}$", message = "must be a plugin folder name") String> testSubplugins,
        @Size(max = 200)
        String signingPasswordCredentialsId,
        @Size(max = 200)
        String prodLicenseCredentialsId,
        @Size(max = 200)
        String testLicenseCredentialsId,
        @Size(max = 200)
        String deliveryGroup,
        @Size(max = 200)
        String deliveryArtifact,
        @Size(max = 300)
        String deliveryPlugin,
        @Size(max = 500)
        String sonarSources,
        @Size(max = 500)
        String sonarTests,
        Boolean sonarFlutterPlugin,
        @Size(max = 500)
        String dartAnalyzeCommand,
        @Size(max = 50)
        @Pattern(regexp = "^[0-9A-Za-z._-]*$", message = "must be a SonarScanner version such as 5.0.1.3006")
        String sonarScannerVersion) {

    public FlutterSettingsDto {
        modules = Text.clean(modules);
        testModules = Text.clean(testModules);
        testSubmodules = Text.clean(testSubmodules);
        testSubplugins = Text.clean(testSubplugins);
        signingPasswordCredentialsId = Text.trimToNull(signingPasswordCredentialsId);
        prodLicenseCredentialsId = Text.trimToNull(prodLicenseCredentialsId);
        testLicenseCredentialsId = Text.trimToNull(testLicenseCredentialsId);
        deliveryGroup = Text.trimToNull(deliveryGroup);
        deliveryArtifact = Text.trimToNull(deliveryArtifact);
        deliveryPlugin = Text.trimToNull(deliveryPlugin);
        sonarSources = Text.trimToNull(sonarSources);
        sonarTests = Text.trimToNull(sonarTests);
        sonarFlutterPlugin = Boolean.TRUE.equals(sonarFlutterPlugin);
        dartAnalyzeCommand = Text.trimToNull(dartAnalyzeCommand);
        sonarScannerVersion = Text.trimToNull(sonarScannerVersion);
    }

    static FlutterSettingsDto from(FlutterSettings source) {
        return new FlutterSettingsDto(source.platform(), source.modules(), source.testModules(),
                source.testSubmodules(), source.testSubplugins(), source.signingPasswordCredentialsId(),
                source.prodLicenseCredentialsId(), source.testLicenseCredentialsId(), source.deliveryGroup(),
                source.deliveryArtifact(), source.deliveryPlugin(), source.sonarSources(), source.sonarTests(),
                source.sonarFlutterPlugin(), source.dartAnalyzeCommand(), source.sonarScannerVersion());
    }

    FlutterSettings toDomain() {
        return new FlutterSettings(platform, modules, testModules, testSubmodules, testSubplugins,
                signingPasswordCredentialsId, prodLicenseCredentialsId, testLicenseCredentialsId, deliveryGroup,
                deliveryArtifact, deliveryPlugin, sonarSources, sonarTests, sonarFlutterPlugin, dartAnalyzeCommand,
                sonarScannerVersion);
    }
}
