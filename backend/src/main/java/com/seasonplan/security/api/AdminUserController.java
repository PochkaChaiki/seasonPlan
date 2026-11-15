package com.seasonplan.security.api;

import com.seasonplan.security.domain.ManagedUser;
import com.seasonplan.security.service.LocalUserAdministration;
import com.seasonplan.security.identity.CurrentActorProvider;

import com.seasonplan.security.identity.ActorRole;
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
    private final CurrentActorProvider actors;

    public AdminUserController(LocalUserAdministration users, CurrentActorProvider actors) {
        this.users = users;
        this.actors = actors;
    }

    @PostMapping
    public ResponseEntity<ManagedUser> create(@RequestBody CreateUserRequest request) {
        var command = new LocalUserAdministration.CreateUserCommand(
                request.username(), request.displayName(), request.password(), request.roles());
        ManagedUser user = users.createUser(actors.currentActor(), command);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(user.id()).toUri();
        return ResponseEntity.created(location).body(user);
    }

    public record CreateUserRequest(String username, String displayName, String password, Set<ActorRole> roles) { }
}
