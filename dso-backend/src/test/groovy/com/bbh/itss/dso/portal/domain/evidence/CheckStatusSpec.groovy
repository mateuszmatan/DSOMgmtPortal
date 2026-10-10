package com.bbh.itss.dso.portal.domain.evidence

import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.BLOCKED
import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.FAIL
import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.NOT_REQUIRED
import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.NO_DATA
import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.PASS
import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.SKIP
import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.WARN
import static com.bbh.itss.dso.portal.domain.evidence.CheckStatus.fromTag

class CheckStatusSpec extends Specification {

    def "the tag #tag reads as #status"() {
        expect:
        fromTag(tag) == status

        where:
        tag            || status
        null           || NO_DATA
        ''             || NO_DATA
        '   '          || NO_DATA
        'PASS'         || PASS
        ' warn '       || WARN
        'Fail'         || FAIL
        'blocked'      || BLOCKED
        'not_required' || NOT_REQUIRED
        'SKIP'         || SKIP
        'no_data'      || NO_DATA
        'SUCCESS'      || NO_DATA
        'not required' || NO_DATA
    }
}
