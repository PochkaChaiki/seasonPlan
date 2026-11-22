package com.seasonplan.response.application.port;

import com.seasonplan.response.domain.CorrectionAction;
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
