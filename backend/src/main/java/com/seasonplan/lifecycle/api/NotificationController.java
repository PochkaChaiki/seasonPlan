package com.seasonplan.lifecycle.api;

import com.seasonplan.lifecycle.application.port.CurrentActorProvider;
import com.seasonplan.lifecycle.application.port.NotificationInboxRepository;
import com.seasonplan.lifecycle.domain.NotificationInboxItem;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@PreAuthorize("isAuthenticated()")
public class NotificationController {
    private final CurrentActorProvider actors;
    private final NotificationInboxRepository inbox;

    public NotificationController(CurrentActorProvider actors, NotificationInboxRepository inbox) {
        this.actors = actors;
        this.inbox = inbox;
    }

    @GetMapping("/notifications")
    public List<NotificationInboxItem> notifications() {
        return inbox.findLatestByRecipient(actors.currentActor().userId());
    }
}
