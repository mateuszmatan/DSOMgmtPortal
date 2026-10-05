package com.bbh.itss.dso.portal.adapter.out.influx

import spock.lang.Specification

class FluxSpec extends Specification {

    def "the value #value is the Flux string #literal"() {
        expect:
        Flux.string(value) == literal

        where:
        value         || literal
        'CERT-gui'    || '"CERT-gui"'
        'say "hi"'    || '"say \\"hi\\""'
        'back\\slash' || '"back\\\\slash"'
        'a${b}'       || '"a\\${b}"'
        ''            || '""'
    }

    def "a missing value is no Flux string"() {
        when:
        Flux.string(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'a Flux string needs a value'
    }

    def "a set of values lists each value once"() {
        expect:
        Flux.strings(['CERT-gui', 'CERT-"api"', 'CERT-gui']) == '"CERT-gui", "CERT-\\"api\\""'
        Flux.strings([]) == ''
    }

    def "#value is a Flux duration"() {
        expect:
        Flux.duration(value) == value

        where:
        value << ['365d', '1h30m', '90d', '2w', '1mo', '1y', '500ms', '10us', '10µs', '5ns', '30s', '1d12h']
    }

    def "#value is not a Flux duration"() {
        when:
        Flux.duration(value)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "'$value' is not a Flux duration such as 365d"

        where:
        value << [null, '', ' ', '365', 'd', '0d', '-1d', '1.5d', '1 d', '365d)', '1x', '1d\n|> drop()']
    }

    def "#value is a positive number"() {
        expect:
        Flux.positive(value) == value

        where:
        value << [1, 30, 730]
    }

    def "#value is not a positive number"() {
        when:
        Flux.positive(value)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "$value is not a positive number"

        where:
        value << [0, -1, Integer.MIN_VALUE]
    }
}
