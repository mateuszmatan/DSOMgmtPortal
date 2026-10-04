package com.bbh.itss.dso.portal.catalog

import spock.lang.Specification

class ConfigTreeSpec extends Specification {

    def tree = new ConfigTree()

    def "values are written by dotted path into nested sections"() {
        when:
        tree.set('tools.sonar.projectKey', 'cert-scanner')
                .set('tools.sonar.projectName', 'CertScanner')
                .set('appId', '109f44ac')
                .set('dast.enabled', false)

        then:
        tree.toMap() == [tools: [sonar: [projectKey: 'cert-scanner', projectName: 'CertScanner']],
                         appId: '109f44ac', dast: [enabled: false]]
    }

    def "a missing value is skipped: #description"() {
        when:
        tree.set('tools.sonar.projectKey', value)

        then:
        tree.toMap() == [:]

        where:
        description  | value
        'null'       | null
        'empty text' | ''
        'blank text' | '  '
        'empty list' | []
    }

    def "a value on the way to a path is replaced by a section"() {
        when:
        tree.set('influx', 'on').set('influx.enabled', true)

        then:
        tree.toMap() == [influx: [enabled: true]]
    }

    def "merging combines sections key by key and replaces other values"() {
        given:
        tree.set('tests.smoke.enabled', true).set('appId', 'old')

        when:
        tree.merge([tests: [smoke: [timeoutMin: 15], regression: [enabled: false]], appId: 'new', includedDirs: ['src']])

        then:
        tree.toMap() == [tests : [smoke: [enabled: true, timeoutMin: 15], regression: [enabled: false]],
                         appId : 'new', includedDirs: ['src']]
    }

    def "sections merged from an immutable map stay editable"() {
        when:
        tree.merge(Map.of('tests', Map.of('smoke', Map.of('timeoutMin', 15))))
        tree.set('tests.smoke.enabled', true)

        then:
        tree.get('tests.smoke') == [timeoutMin: 15, enabled: true]
    }

    def "get follows the path and returns null where it ends"() {
        given:
        tree.set('tools.sonar.projectKey', 'cert').set('appId', 'x')

        expect:
        tree.get('tools.sonar.projectKey') == 'cert'
        tree.get('tools.sonar') == [projectKey: 'cert']
        tree.get('tools.nexusIq.application') == null
        tree.get('appId.value') == null
    }

    def "the map lists the given keys first and the others in insertion order"() {
        given:
        tree.set('tests.enabled', true).set('appId', 'x').set('flutter.platform', 'apk').set('buildTool', 'gradle')

        expect:
        tree.toMap(['appId', 'buildTool', 'deployTarget', 'tests']).keySet() as List == ['appId', 'buildTool', 'tests', 'flutter']
        tree.toMap().keySet() as List == ['tests', 'appId', 'flutter', 'buildTool']
    }

    def "the returned map is a copy"() {
        given:
        tree.set('appId', 'x')

        when:
        tree.toMap().put('appId', 'changed')

        then:
        tree.get('appId') == 'x'
    }
}
