package com.bbh.itss.dso.portal.domain.shared;

public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String entity, Object id) {
        return new NotFoundException(entity + " " + id + " does not exist");
    }
}
