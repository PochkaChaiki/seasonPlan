package com.seasonplan.pollen.adapter.out.persistence;

import com.seasonplan.pollen.application.port.RemoteCommandBoundary;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Do not suspend a caller's season lock: reject it before any intent or remote mutation. */
public final class SpringRemoteCommandBoundary implements RemoteCommandBoundary {
    @Override public void requireOutsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Pollen mutation must start outside a database transaction");
    }
}
