package com.bbh.dso.portal.pipeline;

/**
 * A pipeline presented a key that has been invalidated.
 */
public class KeyRevokedException extends RuntimeException {

    public KeyRevokedException(String message) {
        super(message);
    }
}
