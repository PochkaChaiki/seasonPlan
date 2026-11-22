package com.seasonplan.pollen.application.port;

/** Technical guard implemented by infrastructure: no ambient DB transaction may span provider mutation. */
@FunctionalInterface
public interface RemoteCommandBoundary {
    void requireOutsideTransaction();
}
