package com.bbh.itss.dso.portal.domain.catalog

import com.bbh.itss.dso.portal.domain.shared.ConfigTree
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform.APK
import static com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform.APPBUNDLE

class FlutterSettingsSpec extends Specification {

    static final FlutterSettings FULL = new FlutterSettings(APPBUNDLE, ['app', 'core'], ['app'], ['core'], ['plugin_a'],
            'flutter-signing', 'flutter-prod-license', 'flutter-test-license', 'com.bbh.cert', 'cert-app',
            'com.bbh:flutter-delivery:1.0', 'lib', 'test', true, 'dart analyze --fatal-infos', '5.0.1.3006')

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

    def "the Flutter build stage needs its modules, a test module and all three credentials"() {
        given:
        def problems = new ValidationProblems()

        when:
        FlutterSettings.NONE.validate(problems.at('flutter'), DeployTarget.OPENSHIFT)

        then:
        problems.list()*.field == ['flutter.modules', 'flutter.testModules', 'flutter.signingPasswordCredentialsId',
                                   'flutter.prodLicenseCredentialsId', 'flutter.testLicenseCredentialsId']
        problems.list()*.message == ['add at least one module: the build stage prepares each of them',
                                     'add at least one test module: the unit tests stage runs them'] +
                ['is required: the Flutter build stage reads this Jenkins credential'] * 3
    }

    def "a Flutter build delivered to VMs needs the Nexus coordinates of its delivery"() {
        given:
        def problems = new ValidationProblems()
        def withoutDelivery = new FlutterSettings(APK, ['app'], ['app'], [], [], 'sign', 'prod', 'test', group, artifact,
                plugin, null, null, false, null, null)

        when:
        withoutDelivery.validate(problems, target)

        then:
        problems.list()*.field == missing
        problems.list()*.message.every { it == 'is required for Flutter on VMs: the Nexus delivery uploads the build under it' }

        where:
        target                 | group     | artifact | plugin   || missing
        DeployTarget.VM        | null      | null     | null     || ['deliveryGroup', 'deliveryArtifact', 'deliveryPlugin']
        DeployTarget.VM        | 'com.bbh' | ' '      | 'plugin' || ['deliveryArtifact']
        DeployTarget.VM        | 'com.bbh' | 'app'    | 'plugin' || []
        DeployTarget.OPENSHIFT | null      | null     | null     || []
    }

    def "module lists that do not fit their column are refused"() {
        given:
        def problems = new ValidationProblems()
        def tooMany = (1..30).collect { "modules/feature-$it/${'x' * 25}".toString() }
        def settings = new FlutterSettings(APK, tooMany, tooMany, tooMany, ['ok'], 'sign', 'prod', 'test', null, null,
                null, null, null, false, null, null)

        when:
        settings.validate(problems, DeployTarget.OPENSHIFT)

        then:
        problems.list()*.field == ['modules', 'testModules', 'testSubmodules']
        problems.list()*.message.unique() == ['is too long: all entries together may take at most 1000 bytes']
    }

    def "missing credential #missing is reported"() {
        given:
        def problems = new ValidationProblems()

        when:
        settings.validate(problems, DeployTarget.VM)

        then:
        problems.list()*.field == missing

        where:
        settings                             || missing
        FULL                                 || []
        credentials(null, 'prod', 'test')    || ['signingPasswordCredentialsId']
        credentials('sign', ' ', 'test')     || ['prodLicenseCredentialsId']
        credentials('sign', 'prod', null)    || ['testLicenseCredentialsId']
    }

    private static FlutterSettings credentials(String signing, String prod, String test) {
        new FlutterSettings(null, ['app'], ['app'], [], [], signing, prod, test, 'com.bbh', 'app', 'plugin', null, null,
                false, null, null)
    }

    private static Map written(FlutterSettings settings) {
        def tree = new ConfigTree()
        settings.writeTo(tree)
        tree.toMap()
    }
}
