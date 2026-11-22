package com.seasonplan.season.api;

import com.seasonplan.season.domain.ApprovalCondition;
import com.seasonplan.identity.api.ActorRole;
import com.seasonplan.identity.api.CurrentActor;
import com.seasonplan.audit.api.AuditLogEntry;
import java.util.*;

/** Public commands and queries for the implemented planning/preparation slice. */
public interface SeasonPlanning {
    SeasonFacts createDraft(SeasonDraft draft, ActorRole role, String actorId);
    SeasonFacts updateDraft(UUID id, SeasonDraft draft, ActorRole role, String actorId);
    SeasonFacts submitForApproval(UUID id, ActorRole role, String actorId);
    SeasonFacts verify(UUID id, ActorRole role, String actorId);
    SeasonFacts approve(UUID id, String comment, ActorRole role, String actorId);
    SeasonFacts approve(UUID id, String comment, List<ApprovalCondition> conditions, ActorRole role, String actorId);
    SeasonFacts returnForRevision(UUID id, String comment, ActorRole role, String actorId);
    List<AuditLogEntry> reviewHistory(UUID id, CurrentActor actor);
    Map<String, List<String>> calculateChecklist(UUID id);
    SeasonFacts completeItem(UUID id, String key, ActorRole role, String actorId);
    SeasonFacts markRitualReady(UUID id, ActorRole role, String actorId);
    SeasonFacts get(UUID id);
    List<SeasonFacts> listPlans();
}
