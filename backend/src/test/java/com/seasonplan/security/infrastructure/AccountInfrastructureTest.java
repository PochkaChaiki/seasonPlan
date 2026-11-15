package com.seasonplan.security.infrastructure;

import com.seasonplan.security.exception.UserAdministrationException;
import com.seasonplan.security.identity.ActorRole;
import com.seasonplan.security.infrastructure.jdbc.JdbcUserAccountRepository;
import com.seasonplan.security.infrastructure.spring.SpringAccountTransaction;
import com.seasonplan.security.infrastructure.spring.SpringPasswordHasher;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AccountInfrastructureTest {
    @Test void repositoryWritesOnlyEncodedHashAndRoles() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var repository = new JdbcUserAccountRepository(jdbc);
        UUID id = UUID.randomUUID();
        repository.insert(id, "planner", "Planner", false, Set.of(ActorRole.FAIRY_PLANNER), "{bcrypt}hash");
        verify(jdbc).update("insert into users (id, username, display_name, enabled, system_admin) values (?, ?, ?, true, ?)", id, "planner", "Planner", false);
        verify(jdbc).update("insert into local_credentials (user_id, password_hash) values (?, ?)", id, "{bcrypt}hash");
        verify(jdbc).update("insert into user_roles (user_id, role) values (?, ?)", id, "FAIRY_PLANNER");
        repository.updateCredentials(id, "new-name", "{bcrypt}new");
        verify(jdbc).update("update users set username = ? where id = ?", "new-name", id);
        verify(jdbc).update("update local_credentials set password_hash = ?, updated_at = now() where user_id = ?", "{bcrypt}new", id);
    }

    @Test void credentialLookupLocksTheCurrentHashByUserId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID id = UUID.randomUUID();
        var repository = new JdbcUserAccountRepository(jdbc);
        assertThat(repository.lockCredentialHash(id)).isEmpty();
        verify(jdbc).query(eq("select password_hash from local_credentials where user_id = ? for update"),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<String>>any(), eq(id));
    }

    @Test void duplicateUsernameIsTranslatedWithoutCredentialWrite() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID id = UUID.randomUUID();
        when(jdbc.update("update users set username = ? where id = ?", "taken", id)).thenThrow(new DuplicateKeyException("constraint"));
        assertThatThrownBy(() -> new JdbcUserAccountRepository(jdbc).updateCredentials(id, "taken", "hash"))
                .isInstanceOf(UserAdministrationException.class)
                .satisfies(error -> assertThat(((UserAdministrationException) error).kind()).isEqualTo(UserAdministrationException.Kind.DUPLICATE_USERNAME));
        verify(jdbc, never()).update(contains("local_credentials"), any(), any());
    }

    @Test void transactionAdapterRollsBackExceptionsAndCommitsSuccess() {
        var manager = mock(PlatformTransactionManager.class);
        var status = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(status);
        var transaction = new SpringAccountTransaction(manager);
        assertThat(transaction.execute(() -> "ok")).isEqualTo("ok");
        verify(manager).commit(status);
        assertThatThrownBy(() -> transaction.execute(() -> { throw new IllegalStateException("write failed"); }))
                .isInstanceOf(IllegalStateException.class);
        verify(manager).rollback(status);
    }

    @Test void passwordAdapterDelegatesToConfiguredEncoder() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("secret")).thenReturn("encoded");
        when(encoder.matches("secret", "encoded")).thenReturn(true);
        var hasher = new SpringPasswordHasher(encoder);
        assertThat(hasher.encode("secret")).isEqualTo("encoded");
        assertThat(hasher.matches("secret", "encoded")).isTrue();
    }
}
