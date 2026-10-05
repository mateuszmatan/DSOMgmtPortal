package com.bbh.itss.dso.portal.adapter.out.influx

import com.bbh.itss.dso.portal.support.FakeInfluxDb
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class InfluxQueryClientSpec extends Specification {

    @Shared
    @AutoCleanup
    FakeInfluxDb influx = FakeInfluxDb.start()

    def cleanup() {
        influx.reset()
    }

    def "a Flux query is posted as JSON with the token and the CSV comes back as rows"() {
        given:
        influx.respondWith(',result,table,name\r\n,_result,0,DORA-metrics\r\n')
        def client = new InfluxQueryClient(new InfluxProperties(influx.url, 'DevSecOps', 'DORA-metrics', 'read-token', '365d'),
                RestClient.builder())

        when:
        def rows = client.query('buckets() |> limit(n: 1)')

        then:
        client.configured()
        rows == [[table: '0', name: 'DORA-metrics']]
        with(influx.requests.first()) {
            method == 'POST'
            path == '/api/v2/query'
            query == 'org=DevSecOps'
            authorization == 'Token read-token'
            contentType.startsWith('application/json')
            accept == 'application/csv'
            body == [query: 'buckets() |> limit(n: 1)', type: 'flux', dialect: [header: true, annotations: [], delimiter: ',']]
        }
    }

    def "a missing token is sent empty"() {
        given:
        def client = new InfluxQueryClient(new InfluxProperties(influx.url, 'DevSecOps', 'DORA-metrics', null, '365d'),
                RestClient.builder())

        when:
        def rows = client.query('buckets()')

        then:
        influx.requests.first().authorization.trim() == 'Token'
        rows == [[table: '0', name: 'DORA-metrics']]
    }

    def "an error from InfluxDB fails the query"() {
        given:
        influx.failWith(401)
        def client = new InfluxQueryClient(new InfluxProperties(influx.url, 'DevSecOps', 'DORA-metrics', 'wrong', '365d'),
                RestClient.builder())

        when:
        client.query('buckets()')

        then:
        thrown(HttpClientErrorException.Unauthorized)
    }

    def "without a URL nothing can be queried"() {
        given:
        def client = new InfluxQueryClient(new InfluxProperties(url, 'DevSecOps', 'DORA-metrics', null, '365d'), RestClient.builder())

        when:
        client.query('buckets()')

        then:
        !client.configured()
        def e = thrown(IllegalStateException)
        e.message == 'InfluxDB is not configured'

        where:
        url << [null, ' ']
    }

    def "values are escaped for Flux string literals"() {
        expect:
        InfluxQueryClient.literal(value) == literal

        where:
        value         || literal
        'CERT-gui'    || '"CERT-gui"'
        'say "hi"'    || '"say \\"hi\\""'
        'back\\slash' || '"back\\\\slash"'
        'a${b}'       || '"a\\${b}"'
    }
}
