package com.seasonplan.season.application;

import com.seasonplan.season.api.*;
import com.seasonplan.season.domain.ApprovalCondition;
import com.seasonplan.identity.api.*;
import com.seasonplan.audit.api.AuditLogEntry;
import java.util.*;

/** Isolates immutable public inputs and snapshots command results directly, without rereading state. */
public final class SeasonPlanningFacade implements SeasonPlanning {
    private final SeasonPlanService service;
    private final SeasonQueries queries;
    public SeasonPlanningFacade(SeasonPlanService service, SeasonQueries queries) { this.service = service; this.queries = queries; }
    @Override public SeasonFacts createDraft(SeasonDraft draft, ActorRole role, String actorId) { return SeasonFactMapper.facts(service.createDraft(SeasonFactMapper.draft(draft), role, actorId)); }
    @Override public SeasonFacts updateDraft(UUID id, SeasonDraft draft, ActorRole role, String actorId) { return SeasonFactMapper.facts(service.updateDraft(id, SeasonFactMapper.draft(draft), role, actorId)); }
    @Override public SeasonFacts submitForApproval(UUID id, ActorRole role, String actorId) { return SeasonFactMapper.facts(service.submitForApproval(id, role, actorId)); }
    @Override public SeasonFacts verify(UUID id, ActorRole role, String actorId) { return SeasonFactMapper.facts(service.verify(id, role, actorId)); }
    @Override public SeasonFacts approve(UUID id, String comment, ActorRole role, String actorId) { return SeasonFactMapper.facts(service.approve(id, comment, role, actorId)); }
    @Override public SeasonFacts approve(UUID id, String comment, List<ApprovalCondition> conditions, ActorRole role, String actorId) { return SeasonFactMapper.facts(service.approve(id, comment, conditions == null ? null : Collections.unmodifiableList(new ArrayList<>(conditions)), role, actorId)); }
    @Override public SeasonFacts returnForRevision(UUID id, String comment, ActorRole role, String actorId) { return SeasonFactMapper.facts(service.returnForRevision(id, comment, role, actorId)); }
    @Override public List<AuditLogEntry> reviewHistory(UUID id, CurrentActor actor) { return List.copyOf(service.reviewHistory(id, actor)); }
    @Override public Map<String, List<String>> calculateChecklist(UUID id) { var result = new LinkedHashMap<String, List<String>>(); service.calculateChecklist(id).forEach((key, values) -> result.put(key, List.copyOf(values))); return Collections.unmodifiableMap(result); }
    @Override public SeasonFacts completeItem(UUID id, String key, ActorRole role, String actorId) { return SeasonFactMapper.facts(service.completeItem(id, key, role, actorId)); }
    @Override public SeasonFacts markRitualReady(UUID id, ActorRole role, String actorId) { return SeasonFactMapper.facts(service.markRitualReady(id, role, actorId)); }
    @Override public SeasonFacts get(UUID id) { return queries.get(id); }
    @Override public List<SeasonFacts> listPlans() { return queries.listPlans(); }
}
