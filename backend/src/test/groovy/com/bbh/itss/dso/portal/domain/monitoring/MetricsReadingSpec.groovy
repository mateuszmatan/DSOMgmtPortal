package com.bbh.itss.dso.portal.domain.monitoring

import spock.lang.Specification

class MetricsReadingSpec extends Specification {

    def "a reading holds what the query returned"() {
        when:
        def reading = MetricsReading.of({ -> [1, 2] }, [])

        then:
        reading.value() == [1, 2]
        reading.error() == null
        !reading.failed()
    }

    def "unavailable metrics give the fallback and the reason"() {
        when:
        def reading = MetricsReading.of({ -> throw new MetricsUnavailableException('InfluxDB is down') }, [])

        then:
        reading.value() == []
        reading.error() == 'InfluxDB is down'
        reading.failed()
    }

    def "other failures are not swallowed"() {
        when:
        MetricsReading.of({ -> throw new IllegalStateException('bug') }, [])

        then:
        thrown(IllegalStateException)
    }

    def "a reading can be skipped with the reason of an earlier one"() {
        expect:
        MetricsReading.unavailable([], 'earlier failure') == new MetricsReading([], 'earlier failure')
    }
}
