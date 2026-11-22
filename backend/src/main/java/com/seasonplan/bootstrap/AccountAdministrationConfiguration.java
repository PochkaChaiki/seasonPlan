package com.seasonplan.bootstrap;

import com.seasonplan.identity.adapter.out.persistence.JdbcUserAccountRepository;
import com.seasonplan.identity.adapter.out.spring.SpringAccountTransaction;
import com.seasonplan.identity.adapter.out.spring.SpringPasswordHasher;
import com.seasonplan.identity.application.port.UserAccountRepository;
import com.seasonplan.identity.application.port.AccountTransaction;
import com.seasonplan.identity.api.LocalUserAdministration;
import com.seasonplan.identity.application.LocalUserAdministrationService;
import com.seasonplan.identity.application.port.PasswordHasher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class AccountAdministrationConfiguration {
    @Bean UserAccountRepository userAccountRepository(JdbcTemplate jdbc) { return new JdbcUserAccountRepository(jdbc); }
    @Bean PasswordHasher passwordHasher(PasswordEncoder encoder) { return new SpringPasswordHasher(encoder); }
    @Bean AccountTransaction accountTransaction(PlatformTransactionManager manager) { return new SpringAccountTransaction(manager); }
    @Bean LocalUserAdministration localUserAdministration(UserAccountRepository accounts, PasswordHasher passwords, AccountTransaction transactions) {
        return new LocalUserAdministrationService(accounts, passwords, transactions);
    }
}
