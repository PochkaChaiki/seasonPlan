package com.seasonplan.lifecycle.repository;

import com.seasonplan.lifecycle.domain.CorrectionAction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CorrectionActionRepository {
    Optional<CorrectionAction> find(UUID id);
    Optional<CorrectionAction> byKey(String key);
    List<CorrectionAction> forPlan(UUID planId);
    List<CorrectionAction> allOpen();
    /** deduplicationKey is unique. */
    void save(CorrectionAction action);
}
