package com.bbh.itss.dso.portal.common

import spock.lang.Specification

class TextSpec extends Specification {

    def "blank text is #description"() {
        expect:
        Text.isBlank(value) == blank
        Text.trimToNull(value) == trimmed
        Text.orDefault(value, 'fallback') == (trimmed ?: 'fallback')

        where:
        description           | value     || blank | trimmed
        'null'                | null      || true  | null
        'empty'               | ''        || true  | null
        'whitespace'          | '  \t'    || true  | null
        'text with padding'   | ' main '  || false | 'main'
        'text without spaces' | 'develop' || false | 'develop'
    }
}
