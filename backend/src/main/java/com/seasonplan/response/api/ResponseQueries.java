package com.seasonplan.response.api;

import com.seasonplan.response.domain.MagicIncident;
import com.seasonplan.response.domain.CorrectionAction;
import java.util.List;
import java.util.UUID;

/** Frozen lists of supported immutable incident/action records. Domain guards remain authoritative. */
public interface ResponseQueries {
    List<MagicIncident> incidentsForPlan(UUID planId);
    List<CorrectionAction> correctionsForPlan(UUID planId);
}
