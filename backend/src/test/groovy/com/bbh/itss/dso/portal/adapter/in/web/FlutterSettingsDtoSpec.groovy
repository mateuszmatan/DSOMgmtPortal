package com.bbh.itss.dso.portal.adapter.in.web

import jakarta.validation.Validation
import jakarta.validation.Validator
import spock.lang.Shared
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform.APK
import static com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform.APPBUNDLE

class FlutterSettingsDtoSpec extends Specification {

    static final FlutterSettingsDto FULL = new FlutterSettingsDto(APPBUNDLE, ['app', 'core'], ['app'], ['core'],
            ['plugin_a'], 'flutter-signing', 'flutter-prod-license', 'flutter-test-license', 'com.bbh.cert', 'cert-app',
            'com.bbh:flutter-delivery:1.0', 'lib', 'test', true, 'dart analyze --fatal-infos', '5.0.1.3006')

    @Shared
    Validator validator = Validation.buildDefaultValidatorFactory().validator

    def "a Flutter request is normalised before it is validated"() {
        expect:
        new FlutterSettingsDto(null, [' app ', 'app', ' '], null, null, null, ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', null,
                ' ', ' ') == new FlutterSettingsDto(null, ['app'], [], [], [], null, null, null, null, null, null, null,
                null, false, null, null)
    }

    def "bean validation accepts full Flutter settings"() {
        expect:
        validator.validate(FULL).isEmpty()
        validator.validate(modules([], [], [], [])).isEmpty()
    }

    def "bean validation rejects #description"() {
        expect:
        validator.validate(settings)*.propertyPath*.toString() == [property]

        where:
        description                     | settings                                                   || property
        'a module with a space'         | modules(['my module'], [], [], [])                         || 'modules[0].<list element>'
        'more than 30 modules'          | modules((1..31).collect { "m$it" as String }, [], [], [])  || 'modules'
        'a test module with a colon'    | modules([], ['app:core'], [], [])                          || 'testModules[0].<list element>'
        'a test submodule too long'     | modules([], [], ['s' * 101], [])                           || 'testSubmodules[0].<list element>'
        'a test plugin with a star'     | modules([], [], [], ['plugin*'])                           || 'testSubplugins[0].<list element>'
        'a SonarScanner version w/ ws'  | scannerVersion('5.0 beta')                                 || 'sonarScannerVersion'
        'a too long delivery plugin'    | delivery('p' * 301)                                        || 'deliveryPlugin'
    }

    def "a module that is no folder name is explained"() {
        expect:
        validator.validate(modules(['my module'], [], [], []))*.message == ['must be a module folder name']
        validator.validate(modules([], [], [], ['plugin*']))*.message == ['must be a plugin folder name']
    }

    def "Flutter settings go to the domain and back unchanged"() {
        expect:
        FlutterSettingsDto.from(FULL.toDomain()) == FULL
    }

    private static FlutterSettingsDto modules(List<String> modules, List<String> testModules, List<String> submodules,
                                              List<String> subplugins) {
        new FlutterSettingsDto(APK, modules, testModules, submodules, subplugins, null, null, null, null, null, null, null,
                null, false, null, null)
    }

    private static FlutterSettingsDto scannerVersion(String version) {
        new FlutterSettingsDto(APK, [], [], [], [], null, null, null, null, null, null, null, null, false, null, version)
    }

    private static FlutterSettingsDto delivery(String plugin) {
        new FlutterSettingsDto(APK, [], [], [], [], null, null, null, null, null, plugin, null, null, false, null, null)
    }
}
