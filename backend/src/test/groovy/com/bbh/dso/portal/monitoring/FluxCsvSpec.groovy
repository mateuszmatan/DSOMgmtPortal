package com.bbh.dso.portal.monitoring

import spock.lang.Specification

class FluxCsvSpec extends Specification {

    def "rows are read by column name without the yield column"() {
        given:
        def csv = ',result,table,_time,project,result,build\r\n' +
                ',_result,0,2026-10-01T10:00:00Z,CERT-gui,SUCCESS,12\r\n' +
                ',_result,1,2026-10-02T10:00:00Z,CERT-api,FAILURE,3\r\n'

        expect:
        FluxCsv.parse(csv) == [
                [table: '0', _time: '2026-10-01T10:00:00Z', project: 'CERT-gui', result: 'SUCCESS', build: '12'],
                [table: '1', _time: '2026-10-02T10:00:00Z', project: 'CERT-api', result: 'FAILURE', build: '3']]
    }

    def "tables of a different shape start with their own header after an empty line"() {
        given:
        def csv = ',result,table,project,build\n,_result,0,CERT-gui,12\n\n,result,table,project,branch\n,_result,1,CERT-api,main\n'

        expect:
        FluxCsv.parse(csv) == [[table: '0', project: 'CERT-gui', build: '12'], [table: '1', project: 'CERT-api', branch: 'main']]
    }

    def "a repeated header is not a row"() {
        expect:
        FluxCsv.parse('project,build\nCERT-gui,1\nproject,build\nCERT-api,2') == [[project: 'CERT-gui', build: '1'],
                                                                              [project: 'CERT-api', build: '2']]
    }

    def "columns without a name and cells beyond the header are dropped"() {
        expect:
        FluxCsv.parse(',project,build\n,CERT-gui,1,extra\n,CERT-api') == [[project: 'CERT-gui', build: '1'], [project: 'CERT-api']]
    }

    def "quoted cells may hold commas and quotes"() {
        expect:
        FluxCsv.splitLine('a,"b, c","say ""hi""",') == ['a', 'b, c', 'say "hi"', '']
    }

    def "no CSV means no rows: '#csv'"() {
        expect:
        FluxCsv.parse(csv) == []

        where:
        csv << [null, '', '\r\n']
    }
}
