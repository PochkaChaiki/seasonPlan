package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.application.port.CommandTransaction;
import java.util.UUID;
import java.util.function.Supplier;

/** Unit-test executor only; JDBC transaction/rollback behavior has separate boundary tests. */
public final class DirectTestTransaction implements CommandTransaction {
    @Override public <T> T execute(UUID planId, Supplier<T> command) { return command.get(); }
}
