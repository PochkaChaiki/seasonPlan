package com.seasonplan.security.service;

import java.util.function.Supplier;

public interface AccountTransaction {
    <T> T execute(Supplier<T> action);
}
