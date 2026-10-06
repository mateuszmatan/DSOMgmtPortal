package com.bbh.itss.dso.portal.domain.shared;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class UriEncoding {

    private static final String PATH_SEGMENT_SYMBOLS = "-._~!$&'()*+,;=:@";
    private static final String QUERY_PARAM_SYMBOLS = "-._~!$'()*+,;:@/?";
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private UriEncoding() {
    }

    public static String pathSegment(String value) {
        return encode(value, PATH_SEGMENT_SYMBOLS);
    }

    public static String queryParam(String value) {
        return encode(value, QUERY_PARAM_SYMBOLS);
    }

    public static String decode(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    private static String encode(String value, String allowedSymbols) {
        StringBuilder encoded = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if (isAlphanumeric(c) || (c < 0x80 && allowedSymbols.indexOf(c) >= 0)) {
                encoded.append((char) c);
            } else {
                encoded.append('%').append(HEX[c >> 4]).append(HEX[c & 0xF]);
            }
        }
        return encoded.toString();
    }

    private static boolean isAlphanumeric(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }
}
