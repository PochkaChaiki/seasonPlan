package com.seasonplan.lifecycle.infrastructure.jdbc;

import com.seasonplan.lifecycle.exception.NotFoundException;

import com.seasonplan.lifecycle.service.CommandTransaction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Supplier;

/** Serializes aggregate commands by plan; external mutations must occur before entering this transaction. */
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
            try {
                jdbc.queryForObject("select id from season_plans where id = ? for update", UUID.class, seasonPlanId);
            } catch (org.springframework.dao.EmptyResultDataAccessException missing) {
                throw new com.seasonplan.lifecycle.exception.NotFoundException("season plan not found: " + seasonPlanId);
            }
            return command.get();
        });
    }
}
