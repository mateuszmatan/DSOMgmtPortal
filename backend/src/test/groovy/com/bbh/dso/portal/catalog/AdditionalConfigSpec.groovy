package com.bbh.dso.portal.catalog

import com.bbh.dso.portal.common.ValidationProblems
import spock.lang.Specification

class AdditionalConfigSpec extends Specification {

    def "blank YAML is stored as null: '#yaml'"() {
        expect:
        new AdditionalConfig(yaml).yaml() == null
        new AdditionalConfig(yaml).parse() == [:]

        where:
        yaml << [null, '', '  \n ']
    }

    def "YAML is stored without surrounding whitespace"() {
        expect:
        new AdditionalConfig('\n  tests: {}\n\n').yaml() == 'tests: {}'
    }

    def "the YAML is read as a mapping with text keys"() {
        expect:
        new AdditionalConfig('tests:\n  smoke:\n    enabled: true\n1: one').parse() ==
                [tests: [smoke: [enabled: true]], '1': 'one']
    }

    def "YAML with only a comment is an empty mapping"() {
        expect:
        new AdditionalConfig('# filled in later').parse() == [:]
    }

    def "#description is rejected"() {
        when:
        new AdditionalConfig(yaml).parse()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.startsWith(message)

        where:
        description             | yaml                                       || message
        'a list'                | '- tests\n- deploy'                        || 'must be a YAML mapping'
        'a plain value'         | 'tests'                                    || 'must be a YAML mapping'
        'broken syntax'         | 'tests: [smoke'                            || 'is not valid YAML: '
        'a Java type tag'       | '!!javax.script.ScriptEngineManager [x]'   || 'is not valid YAML: '
    }

    def "the YAML is merged into the config tree"() {
        given:
        def tree = new ConfigTree().set('tests.smoke.enabled', false)

        when:
        new AdditionalConfig('tests:\n  smoke:\n    timeoutMin: 15\ndeploy:\n  vm:\n    host: rdl1').writeTo(tree)

        then:
        tree.toMap() == [tests: [smoke: [enabled: false, timeoutMin: 15]], deploy: [vm: [host: 'rdl1']]]
    }

    def "known keys without secrets are valid"() {
        given:
        def problems = new ValidationProblems()

        when:
        new AdditionalConfig('tests:\n  regression:\n    jobs:\n      - name: r1\n        job: cert/regression\nflutter:\n  platform: apk')
                .validate(problems)

        then:
        problems.isEmpty()
    }

    def "keys the library does not read are reported"() {
        given:
        def problems = new ValidationProblems()

        when:
        new AdditionalConfig('jenkinsfile: Jenkinsfile\nappscanPath: /opt/appscan').validate(problems)

        then:
        problems.list()*.field == ['yaml']
        problems.list()*.message == ["'jenkinsfile' is not a config.yaml key of the DevSecOps library"]
    }

    def "a secret is reported at #path"() {
        given:
        def problems = new ValidationProblems()

        when:
        new AdditionalConfig(yaml).validate(problems)

        then:
        problems.list()*.message == ["'$path' is a secret: store it in Jenkins credentials and reference the credential ID"]

        where:
        yaml                                                              || path
        'asoc:\n  keySecret: s3cr3t'                                      || 'asoc.keySecret'
        'influx:\n  token: abc'                                           || 'influx.token'
        'dast:\n  loginPassword: x'                                       || 'dast.loginPassword'
        'deploy:\n  vm:\n    password: x'                                 || 'deploy.vm.password'
        'tests:\n  regression:\n    jobs:\n      - name: r\n        password: x' || 'tests.regression.jobs.password'
    }

    def "invalid YAML is reported on the yaml field"() {
        given:
        def problems = new ValidationProblems()

        when:
        new AdditionalConfig('- a').validate(problems)

        then:
        problems.list()*.field == ['yaml']
    }
}
