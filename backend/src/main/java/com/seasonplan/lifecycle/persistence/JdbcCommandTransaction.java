package com.seasonplan.lifecycle.persistence;

import com.seasonplan.lifecycle.application.port.CommandTransaction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Supplier;

/** Serializes local completion by plan; external mutations must occur before entering this transaction. */
public final class JdbcCommandTransaction implements CommandTransaction {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public JdbcCommandTransaction(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
    }

    @Override
    public <T> T execute(UUID seasonPlanId, Supplier<T> command) {
        return transaction.execute(status -> {
            jdbc.queryForObject("select id from season_plans where id = ? for update", UUID.class, seasonPlanId);
            return command.get();
        });
    }
}
