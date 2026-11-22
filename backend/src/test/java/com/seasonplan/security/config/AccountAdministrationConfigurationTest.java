package com.seasonplan.security.config;

import com.seasonplan.bootstrap.AccountAdministrationConfiguration;
import com.seasonplan.identity.adapter.out.persistence.JdbcUserAccountRepository;
import com.seasonplan.identity.adapter.out.spring.SpringAccountTransaction;
import com.seasonplan.identity.adapter.out.spring.SpringPasswordHasher;
import com.seasonplan.identity.application.port.UserAccountRepository;
import com.seasonplan.identity.application.port.AccountTransaction;
import com.seasonplan.identity.api.LocalUserAdministration;
import com.seasonplan.identity.application.LocalUserAdministrationService;
import com.seasonplan.identity.application.port.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AccountAdministrationConfigurationTest {
    @Test void composesOneCoreServiceWithRealAdaptersWithoutDatabase() {
        new ApplicationContextRunner().withUserConfiguration(AccountAdministrationConfiguration.class)
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(LocalUserAdministration.class).hasSingleBean(UserAccountRepository.class)
                            .hasSingleBean(PasswordHasher.class).hasSingleBean(AccountTransaction.class);
                    assertThat(context.getBean(LocalUserAdministration.class)).isInstanceOf(LocalUserAdministrationService.class);
                    assertThat(context.getBean(UserAccountRepository.class)).isInstanceOf(JdbcUserAccountRepository.class);
                    assertThat(context.getBean(PasswordHasher.class)).isInstanceOf(SpringPasswordHasher.class);
                    assertThat(context.getBean(AccountTransaction.class)).isInstanceOf(SpringAccountTransaction.class);
                });
    }
}
