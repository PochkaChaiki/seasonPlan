package com.seasonplan.security;

import com.seasonplan.lifecycle.domain.ActorRole;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Set;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {
    private final LocalUserAdministration users;

    public AdminUserController(LocalUserAdministration users) { this.users = users; }

    @PostMapping
    public ResponseEntity<ManagedUser> create(@RequestBody CreateUserRequest request) {
        LocalUserAdministrationService.CreateUserCommand command = LocalUserAdministrationService.validateCreateUser(
                request.username(), request.displayName(), request.password(), request.roles());
        ManagedUser user = users.createUser(command);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(user.id()).toUri();
        return ResponseEntity.created(location).body(user);
    }

    public record CreateUserRequest(String username, String displayName, String password, Set<ActorRole> roles) { }
}
