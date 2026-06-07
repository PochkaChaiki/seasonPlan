package com.seasonplan.lifecycle.domain;

import java.util.UUID;

public class FairyAssignment {
    private UUID id;
    private UUID magicTaskId;
    private String fairyId;
    private String role;
    private String talent;
    private AssignmentStatus status;

    public FairyAssignment() {
    }

    public FairyAssignment(UUID id, UUID magicTaskId, String fairyId, String role, String talent, AssignmentStatus status) {
        this.id = id;
        this.magicTaskId = magicTaskId;
        this.fairyId = fairyId;
        this.role = role;
        this.talent = talent;
        this.status = status;
    }

    public UUID id() {
        return id;
    }

    public UUID magicTaskId() {
        return magicTaskId;
    }

    public String fairyId() {
        return fairyId;
    }

    public String role() {
        return role;
    }

    public String talent() {
        return talent;
    }

    public AssignmentStatus status() {
        return status;
    }
}
