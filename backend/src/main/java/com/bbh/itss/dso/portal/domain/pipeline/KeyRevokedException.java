package com.bbh.itss.dso.portal.domain.pipeline;

import com.bbh.itss.dso.portal.domain.shared.DomainException;

public class KeyRevokedException extends DomainException {

    public KeyRevokedException(PipelineKey key) {
        super("The DevSecOps pipeline key was invalidated on " + key.revokedAt()
                + (key.revokeReason() == null ? "" : ": " + key.revokeReason()));
    }
}
