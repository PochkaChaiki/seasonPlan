package com.seasonplan.identity.adapter.out.spring;

import com.seasonplan.identity.application.port.AccountTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.function.Supplier;

public class SpringAccountTransaction implements AccountTransaction {
    private final TransactionTemplate transactions;
    public SpringAccountTransaction(PlatformTransactionManager manager) { this.transactions = new TransactionTemplate(manager); }
    @Override public <T> T execute(Supplier<T> action) { return transactions.execute(status -> action.get()); }
}
