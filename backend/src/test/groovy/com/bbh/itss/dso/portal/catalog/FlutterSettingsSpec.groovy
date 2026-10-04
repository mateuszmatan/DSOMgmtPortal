package com.bbh.itss.dso.portal.catalog

import com.bbh.itss.dso.portal.common.ValidationProblems
import jakarta.validation.Validation
import jakarta.validation.Validator
import spock.lang.Shared
import spock.lang.Specification

import static com.bbh.itss.dso.portal.catalog.FlutterPlatform.APK
import static com.bbh.itss.dso.portal.catalog.FlutterPlatform.APPBUNDLE

class FlutterSettingsSpec extends Specification {

    static final FlutterSettings FULL = new FlutterSettings(APPBUNDLE, ['app', 'core'], ['app'], ['core'], ['plugin_a'],
            'flutter-signing', 'flutter-prod-license', 'flutter-test-license', 'com.bbh.cert', 'cert-app',
            'com.bbh:flutter-delivery:1.0', 'lib', 'test', true, 'dart analyze --fatal-infos', '5.0.1.3006')

    @Shared
    Validator validator = Validation.buildDefaultValidatorFactory().validator

    def "Flutter settings trim their values and keep each module once"() {
        when:
        def flutter = new FlutterSettings(null, [' app ', 'app', ''], null, [' core '], [' '], ' ', ' ', ' ', ' ', ' ', ' ',
                ' ', ' ', null, ' ', ' ')

        then:
        flutter.platform() == null
        flutter.modules() == ['app']
        flutter.testModules() == []
        flutter.testSubmodules() == ['core']
        flutter.testSubplugins() == []
        flutter.signingPasswordCredentialsId() == null
        flutter.prodLicenseCredentialsId() == null
        flutter.testLicenseCredentialsId() == null
        flutter.deliveryGroup() == null
        flutter.deliveryArtifact() == null
        flutter.deliveryPlugin() == null
        flutter.sonarSources() == null
        flutter.sonarTests() == null
        !flutter.sonarFlutterPlugin()
        flutter.dartAnalyzeCommand() == null
        flutter.sonarScannerVersion() == null
        FlutterSettings.NONE == new FlutterSettings(null, null, null, null, null, ' ', null, null, null, null, null, null,
                null, null, null, null)
    }

    def "Flutter settings that set nothing write nothing"() {
        expect:
        written(FlutterSettings.NONE) == [:]
    }

    def "every Flutter setting is written under the key the library reads"() {
        expect:
        written(FULL) == [flutter : [platform: 'appbundle'],
                          tools   : [flutter: [flutterModules: ['app', 'core']],
                                     sonar  : [sources            : 'lib', tests: 'test', dartAnalyzeCommand: 'dart analyze --fatal-infos',
                                               sonarScannerVersion: '5.0.1.3006', flutterPlugin: true]],
                          tests   : [modules: ['app'], submodules: ['core'], subplugins: ['plugin_a']],
                          build   : [credentialsId: ['flutter-signing', 'flutter-prod-license', 'flutter-test-license']],
                          delivery: [group: 'com.bbh.cert', artifact: 'cert-app', plugin: 'com.bbh:flutter-delivery:1.0']]
    }

    def "the build credentials are written only when all three are set: #signing, #prod, #test"() {
        expect:
        written(credentials(signing, prod, test)).build?.credentialsId == expected

        where:
        signing | prod   | test   || expected
        'sign'  | 'prod' | 'test' || ['sign', 'prod', 'test']
        null    | 'prod' | 'test' || null
        'sign'  | null   | 'test' || null
        'sign'  | 'prod' | null   || null
        null    | null   | null   || null
    }

    def "the SonarQube Flutter plugin is written only when it is used"() {
        expect:
        written(new FlutterSettings(APK, [], [], [], [], null, null, null, null, null, null, 'lib', null, false, null, null)) ==
                [flutter: [platform: 'apk'], tools: [sonar: [sources: 'lib']]]
    }

    def "the Flutter build stage needs all three credentials"() {
        given:
        def problems = new ValidationProblems()

        when:
        FlutterSettings.NONE.validate(problems.at('flutter'))

        then:
        problems.list()*.field == ['flutter.signingPasswordCredentialsId', 'flutter.prodLicenseCredentialsId',
                                   'flutter.testLicenseCredentialsId']
        problems.list()*.message.unique() == ['is required: the Flutter build stage reads this Jenkins credential']
    }

    def "missing credential #missing is reported"() {
        given:
        def problems = new ValidationProblems()

        when:
        settings.validate(problems)

        then:
        problems.list()*.field == missing

        where:
        settings                             || missing
        FULL                                 || []
        credentials(null, 'prod', 'test')    || ['signingPasswordCredentialsId']
        credentials('sign', ' ', 'test')     || ['prodLicenseCredentialsId']
        credentials('sign', 'prod', null)    || ['testLicenseCredentialsId']
    }

    def "Flutter platforms are written in lower case"() {
        expect:
        FlutterPlatform.values()*.configValue() == ['apk', 'appbundle', 'ios', 'macos', 'linux', 'windows', 'web']
    }

    def "bean validation accepts full Flutter settings"() {
        expect:
        validator.validate(FULL).isEmpty()
        validator.validate(FlutterSettings.NONE).isEmpty()
    }

    def "bean validation rejects #description"() {
        expect:
        validator.validate(settings)*.propertyPath*.toString() == [property]

        where:
        description                     | settings                                         || property
        'a module with a space'         | modules(['my module'], [], [], [])               || 'modules[0].<list element>'
        'more than 30 modules'          | modules((1..31).collect { "m$it" as String }, [], [], []) || 'modules'
        'a test module with a colon'    | modules([], ['app:core'], [], [])                || 'testModules[0].<list element>'
        'a test submodule too long'     | modules([], [], ['s' * 101], [])                 || 'testSubmodules[0].<list element>'
        'a test plugin with a star'     | modules([], [], [], ['plugin*'])                 || 'testSubplugins[0].<list element>'
        'a SonarScanner version w/ ws'  | scannerVersion('5.0 beta')                       || 'sonarScannerVersion'
        'a too long delivery plugin'    | delivery('p' * 301)                              || 'deliveryPlugin'
    }

    private static FlutterSettings credentials(String signing, String prod, String test) {
        new FlutterSettings(null, [], [], [], [], signing, prod, test, null, null, null, null, null, false, null, null)
    }

    private static FlutterSettings modules(List<String> modules, List<String> testModules, List<String> submodules,
                                           List<String> subplugins) {
        new FlutterSettings(APK, modules, testModules, submodules, subplugins, null, null, null, null, null, null, null,
                null, false, null, null)
    }

    private static FlutterSettings scannerVersion(String version) {
        new FlutterSettings(APK, [], [], [], [], null, null, null, null, null, null, null, null, false, null, version)
    }

    private static FlutterSettings delivery(String plugin) {
        new FlutterSettings(APK, [], [], [], [], null, null, null, null, null, plugin, null, null, false, null, null)
    }

    private static Map written(FlutterSettings settings) {
        def tree = new ConfigTree()
        settings.writeTo(tree)
        tree.toMap()
    }
}
