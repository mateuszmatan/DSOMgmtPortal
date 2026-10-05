package com.bbh.itss.dso.portal.domain.shared

import com.bbh.itss.dso.portal.domain.catalog.FlutterPlatform
import spock.lang.Specification

class ConfigTreeSpec extends Specification {

    def tree = new ConfigTree()

    def "values, flags that are on and enumerated values in lower case are written by dotted path"() {
        when:
        tree.set('tools.sonar.projectKey', 'cert-scanner')
                .set('tools.sonar.projectName', 'CertScanner')
                .set('appId', '109f44ac')
                .set('dast.enabled', false)
                .set('flutter.platform', FlutterPlatform.APPBUNDLE)
                .flag('flutter.sonar', true)
                .flag('flutter.skipped', false)

        then:
        tree.toMap() == [tools  : [sonar: [projectKey: 'cert-scanner', projectName: 'CertScanner']],
                         appId  : '109f44ac', dast: [enabled: false],
                         flutter: [platform: 'appbundle', sonar: true]]
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
        'empty map'  | [:]
        'empty set'  | [] as Set
    }

    def "false, zero and maps or lists with entries are written"() {
        when:
        tree.set('dast.enabled', false)
                .set('tests.maxParallel', 0)
                .set('tests.smoke.jobs', [[job: 'CERT/smoke']])
                .set('build.gradle.env', [CI: 'true'])

        then:
        tree.toMap() == [dast : [enabled: false],
                         tests: [maxParallel: 0, smoke: [jobs: [[job: 'CERT/smoke']]]],
                         build: [gradle: [env: [CI: 'true']]]]
    }

    def "a skipped value creates no section on its way"() {
        when:
        tree.set('build.gradle.env', [:]).set('deploy.vm.dod.applications', []).set('tools.sonar.projectKey', ' ')

        then:
        tree.toMap() == [:]
        tree.get('build') == null
    }

    def "a later value replaces the earlier one at the same path"() {
        when:
        tree.set('tests.maxParallel', 2).set('tests.maxParallel', 4).set('tests.maxParallel', null)

        then:
        tree.toMap() == [tests: [maxParallel: 4]]
    }

    def "a default is filled into a section that is there and does not have the key"() {
        given:
        tree.set('deploy.vm.rd.deployDir', '/opt/cert')

        when:
        tree.fillIn('deploy.vm.rd', 'host', 'rdltaapps1.testbbh.com').fillIn('deploy.vm.rd', 'user', 'dsoadm')

        then:
        tree.get('deploy.vm.rd') == [deployDir: '/opt/cert', host: 'rdltaapps1.testbbh.com', user: 'dsoadm']
        tree.get('deploy.vm.rd').keySet() as List == ['deployDir', 'host', 'user']
    }

    def "a default never replaces the service's own value"() {
        given:
        tree.set('deploy.vm.rd.host', 'own.host')

        when:
        tree.fillIn('deploy.vm.rd', 'host', 'default.host')

        then:
        tree.get('deploy.vm.rd') == [host: 'own.host']
    }

    def "a default never creates a section the service did not configure"() {
        given:
        tree.set('deploy.vm.rd.host', 'rd.host').set('appId', 'x')

        when:
        tree.fillIn('deploy.vm.qc', 'host', 'qc.host')
                .fillIn('deploy.vm.dod', 'siteName', 'BBH')
                .fillIn('appId', 'value', 'y')
                .fillIn('deploy.vm.rd.host', 'user', 'dsoadm')

        then:
        tree.toMap() == [deploy: [vm: [rd: [host: 'rd.host']]], appId: 'x']
    }

    def "a missing default is not filled in: #description"() {
        given:
        tree.set('deploy.vm.rd.deployDir', '/opt/cert')

        when:
        tree.fillIn('deploy.vm.rd', 'host', value)

        then:
        tree.get('deploy.vm.rd') == [deployDir: '/opt/cert']

        where:
        description  | value
        'null'       | null
        'blank text' | ' '
        'empty list' | []
        'empty map'  | [:]
    }

    def "a value set only when absent creates its section and never replaces a value that is there"() {
        given:
        tree.set('deploy.vm.rd.host', 'own.host')

        when:
        tree.setIfAbsent('deploy.vm.rd.host', 'default.host')
                .setIfAbsent('deploy.vm.rd.user', 'taadmin')
                .setIfAbsent('deploy.vm.qc.host', 'qc.host')
                .setIfAbsent('deploy.vm.qc.user', null)

        then:
        tree.toMap() == [deploy: [vm: [rd: [host: 'own.host', user: 'taadmin'], qc: [host: 'qc.host']]]]
    }

    def "a default may be filled into a top-level section and into a section merged from a map"() {
        given:
        tree.merge([tests: [smoke: [enabled: true]]])

        when:
        tree.fillIn('tests', 'maxParallel', 2).fillIn('tests.smoke', 'timeoutMin', 15)

        then:
        tree.toMap() == [tests: [smoke: [enabled: true, timeoutMin: 15], maxParallel: 2]]
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
