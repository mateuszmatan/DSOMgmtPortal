package com.bbh.itss.dso.portal.domain.shared;

public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
