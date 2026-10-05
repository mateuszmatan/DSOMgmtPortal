package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform;
import com.bbh.itss.dso.portal.domain.catalog.FlutterSettings;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.type.NumericBooleanConverter;

import java.util.List;

@Embeddable
public record FlutterSettingsEmbeddable(
        @Enumerated(EnumType.STRING)
        @Column(name = "FLUTTER_PLATFORM", length = 20) FlutterPlatform platform,
        @Convert(converter = DelimitedListConverter.Lines.class)
        @Column(name = "FLUTTER_MODULES", length = 1000) List<String> modules,
        @Convert(converter = DelimitedListConverter.Lines.class)
        @Column(name = "FLUTTER_TEST_MODULES", length = 1000) List<String> testModules,
        @Convert(converter = DelimitedListConverter.Lines.class)
        @Column(name = "FLUTTER_TEST_SUBMODULES", length = 1000) List<String> testSubmodules,
        @Convert(converter = DelimitedListConverter.Lines.class)
        @Column(name = "FLUTTER_TEST_SUBPLUGINS", length = 1000) List<String> testSubplugins,
        @Column(name = "FLUTTER_SIGNING_CREDENTIALS_ID", length = 200) String signingPasswordCredentialsId,
        @Column(name = "FLUTTER_PROD_LICENSE_CREDENTIALS_ID", length = 200) String prodLicenseCredentialsId,
        @Column(name = "FLUTTER_TEST_LICENSE_CREDENTIALS_ID", length = 200) String testLicenseCredentialsId,
        @Column(name = "FLUTTER_DELIVERY_GROUP", length = 200) String deliveryGroup,
        @Column(name = "FLUTTER_DELIVERY_ARTIFACT", length = 200) String deliveryArtifact,
        @Column(name = "FLUTTER_DELIVERY_PLUGIN", length = 300) String deliveryPlugin,
        @Column(name = "FLUTTER_SONAR_SOURCES", length = 500) String sonarSources,
        @Column(name = "FLUTTER_SONAR_TESTS", length = 500) String sonarTests,
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "FLUTTER_SONAR_PLUGIN", nullable = false) Boolean sonarFlutterPlugin,
        @Column(name = "FLUTTER_DART_ANALYZE_COMMAND", length = 500) String dartAnalyzeCommand,
        @Column(name = "FLUTTER_SONAR_SCANNER_VERSION", length = 50) String sonarScannerVersion) {

    static FlutterSettingsEmbeddable of(FlutterSettings flutter) {
        return new FlutterSettingsEmbeddable(flutter.platform(), flutter.modules(), flutter.testModules(),
                flutter.testSubmodules(), flutter.testSubplugins(), flutter.signingPasswordCredentialsId(),
                flutter.prodLicenseCredentialsId(), flutter.testLicenseCredentialsId(), flutter.deliveryGroup(),
                flutter.deliveryArtifact(), flutter.deliveryPlugin(), flutter.sonarSources(), flutter.sonarTests(),
                flutter.sonarFlutterPlugin(), flutter.dartAnalyzeCommand(), flutter.sonarScannerVersion());
    }

    FlutterSettings toDomain() {
        return new FlutterSettings(platform, modules, testModules, testSubmodules, testSubplugins,
                signingPasswordCredentialsId, prodLicenseCredentialsId, testLicenseCredentialsId, deliveryGroup,
                deliveryArtifact, deliveryPlugin, sonarSources, sonarTests, sonarFlutterPlugin, dartAnalyzeCommand,
                sonarScannerVersion);
    }
}
