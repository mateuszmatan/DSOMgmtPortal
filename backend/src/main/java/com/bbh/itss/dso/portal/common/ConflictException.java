package com.bbh.itss.dso.portal.common;

/**
 * The request is valid on its own but clashes with data already stored, for example a duplicate product code.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
