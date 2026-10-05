package com.bbh.itss.dso.portal.adapter.out.influx

import com.bbh.itss.dso.portal.domain.monitoring.MetricsUnavailableException
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

    def "a reading of a configured client gives what was read"() {
        given:
        def client = new InfluxQueryClient(new InfluxProperties(influx.url, 'DevSecOps', 'DORA-metrics', null, '365d'),
                RestClient.builder())

        expect:
        client.read { 'read' } == 'read'
        client.bucket() == '"DORA-metrics"'
        client.lastRunLookback() == '365d'
    }

    def "without a URL nothing is read and the reason is given"() {
        given:
        def client = new InfluxQueryClient(new InfluxProperties(null, 'DevSecOps', 'DORA-metrics', null, '365d'),
                RestClient.builder())
        def reads = 0

        when:
        client.read { reads++ }

        then:
        reads == 0
        def e = thrown(MetricsUnavailableException)
        e.message == 'InfluxDB is not configured for the portal'

        when:
        client.requireConfigured()

        then:
        thrown(MetricsUnavailableException)
    }

    def "a failed reading gives the reason, cut to 300 characters"() {
        given:
        def client = new InfluxQueryClient(new InfluxProperties(influx.url, 'DevSecOps', 'DORA-metrics', null, '365d'),
                RestClient.builder())

        when:
        client.read { throw failure }

        then:
        def e = thrown(MetricsUnavailableException)
        e.message == error

        where:
        failure                                       || error
        new IllegalStateException('401 Unauthorized') || 'InfluxDB could not be read: 401 Unauthorized'
        new IllegalStateException('x' * 300)          || 'InfluxDB could not be read: ' + 'x' * 300
        new IllegalStateException('0123456789' * 40)  || 'InfluxDB could not be read: ' + ('0123456789' * 30)
        new RuntimeException()                        || 'InfluxDB could not be read: RuntimeException'
        new MetricsUnavailableException('kept')       || 'kept'
    }

    def "an error from InfluxDB read through the client gives its status"() {
        given:
        influx.failWith(401)
        def client = new InfluxQueryClient(new InfluxProperties(influx.url, 'DevSecOps', 'DORA-metrics', 'wrong', '365d'),
                RestClient.builder())

        when:
        client.read { client.query('buckets()') }

        then:
        def e = thrown(MetricsUnavailableException)
        e.message.startsWith('InfluxDB could not be read: 401 Unauthorized')
    }

    def "InfluxDB is configured with a URL"() {
        expect:
        new InfluxProperties(url, 'DevSecOps', 'DORA-metrics', null, '365d').configured() == configured

        where:
        url                     || configured
        null                    || false
        ' '                     || false
        'http://localhost:8086' || true
    }

    def "the look-back for the latest runs must be a Flux duration"() {
        when:
        new InfluxProperties('http://localhost:8086', 'DevSecOps', 'DORA-metrics', null, lookback)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "'$lookback' is not a Flux duration such as 365d"

        where:
        lookback << [null, '', '365', '-1d', '1d) |> drop()', '0d']
    }
}
