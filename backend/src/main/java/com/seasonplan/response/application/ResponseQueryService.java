package com.seasonplan.response.application;

import com.seasonplan.response.api.ResponseQueries;
import com.seasonplan.response.application.port.MagicIncidentRepository;
import com.seasonplan.response.application.port.CorrectionActionRepository;
import com.seasonplan.response.domain.MagicIncident;
import com.seasonplan.response.domain.CorrectionAction;
import java.util.List;
import java.util.UUID;

public final class ResponseQueryService implements ResponseQueries {
    private final MagicIncidentRepository incidents;
    private final CorrectionActionRepository actions;
    public ResponseQueryService(MagicIncidentRepository incidents, CorrectionActionRepository actions) {
        this.incidents = incidents; this.actions = actions;
    }
    @Override public List<MagicIncident> incidentsForPlan(UUID planId) { return List.copyOf(incidents.forPlan(planId)); }
    @Override public List<CorrectionAction> correctionsForPlan(UUID planId) { return List.copyOf(actions.forPlan(planId)); }
}
