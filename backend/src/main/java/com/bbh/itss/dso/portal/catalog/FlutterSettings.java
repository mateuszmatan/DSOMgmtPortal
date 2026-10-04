package com.bbh.itss.dso.portal.catalog;

import com.bbh.itss.dso.portal.common.DelimitedListConverter;
import com.bbh.itss.dso.portal.common.Text;
import com.bbh.itss.dso.portal.common.ValidationProblems;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.type.NumericBooleanConverter;

import java.util.List;

@Embeddable
public record FlutterSettings(
        @Enumerated(EnumType.STRING)
        @Column(name = "FLUTTER_PLATFORM", length = 20)
        FlutterPlatform platform,
        @Size(max = 30) @Convert(converter = DelimitedListConverter.Lines.class)
        @Column(name = "FLUTTER_MODULES", length = 1000)
        List<@Pattern(regexp = "^[A-Za-z0-9._/-]{1,100}$", message = "must be a module folder name") String> modules,
        @Size(max = 30) @Convert(converter = DelimitedListConverter.Lines.class)
        @Column(name = "FLUTTER_TEST_MODULES", length = 1000)
        List<@Pattern(regexp = "^[A-Za-z0-9._/-]{1,100}$", message = "must be a module folder name") String> testModules,
        @Size(max = 30) @Convert(converter = DelimitedListConverter.Lines.class)
        @Column(name = "FLUTTER_TEST_SUBMODULES", length = 1000)
        List<@Pattern(regexp = "^[A-Za-z0-9._/-]{1,100}$", message = "must be a module folder name") String> testSubmodules,
        @Size(max = 30) @Convert(converter = DelimitedListConverter.Lines.class)
        @Column(name = "FLUTTER_TEST_SUBPLUGINS", length = 1000)
        List<@Pattern(regexp = "^[A-Za-z0-9._/-]{1,100}$", message = "must be a plugin folder name") String> testSubplugins,
        @Size(max = 200)
        @Column(name = "FLUTTER_SIGNING_CREDENTIALS_ID", length = 200)
        String signingPasswordCredentialsId,
        @Size(max = 200)
        @Column(name = "FLUTTER_PROD_LICENSE_CREDENTIALS_ID", length = 200)
        String prodLicenseCredentialsId,
        @Size(max = 200)
        @Column(name = "FLUTTER_TEST_LICENSE_CREDENTIALS_ID", length = 200)
        String testLicenseCredentialsId,
        @Size(max = 200)
        @Column(name = "FLUTTER_DELIVERY_GROUP", length = 200)
        String deliveryGroup,
        @Size(max = 200)
        @Column(name = "FLUTTER_DELIVERY_ARTIFACT", length = 200)
        String deliveryArtifact,
        @Size(max = 300)
        @Column(name = "FLUTTER_DELIVERY_PLUGIN", length = 300)
        String deliveryPlugin,
        @Size(max = 500)
        @Column(name = "FLUTTER_SONAR_SOURCES", length = 500)
        String sonarSources,
        @Size(max = 500)
        @Column(name = "FLUTTER_SONAR_TESTS", length = 500)
        String sonarTests,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "FLUTTER_SONAR_PLUGIN", nullable = false)
        Boolean sonarFlutterPlugin,
        @Size(max = 500)
        @Column(name = "FLUTTER_DART_ANALYZE_COMMAND", length = 500)
        String dartAnalyzeCommand,
        @Size(max = 50)
        @Pattern(regexp = "^[0-9A-Za-z._-]*$", message = "must be a SonarScanner version such as 5.0.1.3006")
        @Column(name = "FLUTTER_SONAR_SCANNER_VERSION", length = 50)
        String sonarScannerVersion) {

    public static final FlutterSettings NONE = new FlutterSettings(null, List.of(), List.of(), List.of(), List.of(), null,
            null, null, null, null, null, null, null, false, null, null);

    public FlutterSettings {
        modules = DelimitedListConverter.clean(modules);
        testModules = DelimitedListConverter.clean(testModules);
        testSubmodules = DelimitedListConverter.clean(testSubmodules);
        testSubplugins = DelimitedListConverter.clean(testSubplugins);
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
