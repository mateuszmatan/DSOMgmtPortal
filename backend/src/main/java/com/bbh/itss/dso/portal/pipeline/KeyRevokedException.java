package com.bbh.itss.dso.portal.pipeline;

/**
 * A pipeline presented a key that has been invalidated.
 */
public class KeyRevokedException extends RuntimeException {

    public KeyRevokedException(PipelineKey key) {
        super("The DevSecOps pipeline key was invalidated on " + key.getRevokedAt()
                + (key.getRevokeReason() == null ? "" : ": " + key.getRevokeReason()));
    }
}
