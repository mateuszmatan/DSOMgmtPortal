package com.bbh.itss.dso.portal.pipeline;

public class KeyRevokedException extends RuntimeException {

    public KeyRevokedException(PipelineKey key) {
        super("The DevSecOps pipeline key was invalidated on " + key.getRevokedAt()
                + (key.getRevokeReason() == null ? "" : ": " + key.getRevokeReason()));
    }
}
