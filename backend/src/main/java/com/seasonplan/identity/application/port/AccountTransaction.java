package com.seasonplan.identity.application.port;

import java.util.function.Supplier;

public interface AccountTransaction {
    <T> T execute(Supplier<T> action);
}
