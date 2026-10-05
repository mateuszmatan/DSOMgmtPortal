package com.bbh.itss.dso.portal.domain.shared

import org.springframework.web.util.UriUtils
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class UriEncodingSpec extends Specification {

    static final String EVERY_SYMBOL = (32..126).collect { (char) it }.join('') + 'é€\u007f'

    def "a path segment keeps the characters RFC 3986 allows in it and encodes the rest in upper case hex"() {
        expect:
        UriEncoding.pathSegment(segment) == encoded

        where:
        segment                       || encoded
        'AZaz09-._~'                  || 'AZaz09-._~'
        "!\$&'()*+,;=:@"              || "!\$&'()*+,;=:@"
        'a b/c?d#e%f[g]h"i<j>k\\l^m`' || 'a%20b%2Fc%3Fd%23e%25f%5Bg%5Dh%22i%3Cj%3Ek%5Cl%5Em%60'
        '{|}'                         || '%7B%7C%7D'
        'é€'                          || '%C3%A9%E2%82%AC'
        '\u007f'                      || '%7F'
    }

    def "a query parameter also keeps slashes and question marks but encodes the separators of parameters"() {
        expect:
        UriEncoding.queryParam(value) == encoded

        where:
        value                 || encoded
        'cert-gui'            || 'cert-gui'
        'com.bbh:cert gui'    || 'com.bbh:cert%20gui'
        'a/b?c'               || 'a/b?c'
        'a&b=c#d'             || 'a%26b%3Dc%23d'
        "+!\$'()*,;@"         || "+!\$'()*,;@"
        'Zażółć'              || 'Za%C5%BC%C3%B3%C5%82%C4%87'
    }

    def "the encodings match the ones Spring applies to path segments and query parameters"() {
        expect:
        UriEncoding.pathSegment(EVERY_SYMBOL) == UriUtils.encodePathSegment(EVERY_SYMBOL, StandardCharsets.UTF_8)
        UriEncoding.queryParam(EVERY_SYMBOL) == UriUtils.encodeQueryParam(EVERY_SYMBOL, StandardCharsets.UTF_8)
    }
}
