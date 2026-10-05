package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.Text;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.List;

public record FlutterSettings(
        FlutterPlatform platform,
        List<String> modules,
        List<String> testModules,
        List<String> testSubmodules,
        List<String> testSubplugins,
        String signingPasswordCredentialsId,
        String prodLicenseCredentialsId,
        String testLicenseCredentialsId,
        String deliveryGroup,
        String deliveryArtifact,
        String deliveryPlugin,
        String sonarSources,
        String sonarTests,
        Boolean sonarFlutterPlugin,
        String dartAnalyzeCommand,
        String sonarScannerVersion) {

    public static final FlutterSettings NONE = new FlutterSettings(null, List.of(), List.of(), List.of(), List.of(), null,
            null, null, null, null, null, null, null, false, null, null);

    public FlutterSettings {
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

    public void writeTo(ConfigTree config) {
        config.set("flutter.platform", platform == null ? null : platform.configValue())
                .set("tools.flutter.flutterModules", modules)
                .set("tests.modules", testModules)
                .set("tests.submodules", testSubmodules)
                .set("tests.subplugins", testSubplugins);
        if (signingPasswordCredentialsId != null && prodLicenseCredentialsId != null && testLicenseCredentialsId != null) {
            config.set("build.credentialsId", List.of(signingPasswordCredentialsId, prodLicenseCredentialsId,
                    testLicenseCredentialsId));
        }
        config.set("delivery.group", deliveryGroup)
                .set("delivery.artifact", deliveryArtifact)
                .set("delivery.plugin", deliveryPlugin)
                .set("tools.sonar.sources", sonarSources)
                .set("tools.sonar.tests", sonarTests)
                .set("tools.sonar.dartAnalyzeCommand", dartAnalyzeCommand)
                .set("tools.sonar.sonarScannerVersion", sonarScannerVersion);
        if (sonarFlutterPlugin) {
            config.set("tools.sonar.flutterPlugin", true);
        }
    }

    public void validate(ValidationProblems problems) {
        String message = "is required: the Flutter build stage reads this Jenkins credential";
        if (signingPasswordCredentialsId == null) {
            problems.add("signingPasswordCredentialsId", message);
        }
        if (prodLicenseCredentialsId == null) {
            problems.add("prodLicenseCredentialsId", message);
        }
        if (testLicenseCredentialsId == null) {
            problems.add("testLicenseCredentialsId", message);
        }
    }
}
