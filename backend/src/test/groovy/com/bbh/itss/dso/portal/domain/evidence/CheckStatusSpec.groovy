package com.bbh.itss.dso.portal.domain.evidence

import spock.lang.Specification

class CheckStatusSpec extends Specification {

    def "the tag #tag reads as #status"() {
        expect:
        CheckStatus.fromTag(tag) == status

        where:
        tag            || status
        null           || CheckStatus.NO_DATA
        ''             || CheckStatus.NO_DATA
        '   '          || CheckStatus.NO_DATA
        'PASS'         || CheckStatus.PASS
        ' warn '       || CheckStatus.WARN
        'Fail'         || CheckStatus.FAIL
        'blocked'      || CheckStatus.BLOCKED
        'not_required' || CheckStatus.NOT_REQUIRED
        'SKIP'         || CheckStatus.SKIP
        'no_data'      || CheckStatus.NO_DATA
        'SUCCESS'      || CheckStatus.NO_DATA
        'not required' || CheckStatus.NO_DATA
    }
}
