package com.seasonplan.security.config;

import com.seasonplan.security.infrastructure.jdbc.JdbcUserAccountRepository;
import com.seasonplan.security.infrastructure.spring.SpringAccountTransaction;
import com.seasonplan.security.infrastructure.spring.SpringPasswordHasher;
import com.seasonplan.security.repository.UserAccountRepository;
import com.seasonplan.security.service.AccountTransaction;
import com.seasonplan.security.service.LocalUserAdministration;
import com.seasonplan.security.service.LocalUserAdministrationService;
import com.seasonplan.security.service.PasswordHasher;
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
