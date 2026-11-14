package com.seasonplan.lifecycle.domain;

import java.util.List;
import java.util.Map;

/** Immutable condition recorded as part of an approval decision. */
public record ApprovalCondition(String key, String displayText, String evidenceRequirement, ActorRole ownerRole) {
    public ApprovalCondition {
        if (key == null || key.isBlank() || displayText == null || displayText.isBlank()
                || evidenceRequirement == null || evidenceRequirement.isBlank() || ownerRole == null) {
            throw new DomainValidationException(Map.of("conditions", List.of("each condition requires key, display text, evidence requirement and owner role")));
        }
    }
}
