package com.bbh.itss.dso.portal.domain.shared;

public class ConflictException extends DomainException {

    public static final String STALE_VERSION =
            "The record was changed by someone else in the meantime. Reload it and apply your change again.";

    public ConflictException(String message) {
        super(message);
    }

    public static ConflictException staleVersion() {
        return new ConflictException(STALE_VERSION);
    }
}
