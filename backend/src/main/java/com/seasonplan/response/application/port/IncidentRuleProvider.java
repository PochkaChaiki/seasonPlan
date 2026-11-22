package com.seasonplan.response.application.port;

import com.seasonplan.response.domain.MagicIncident;

public interface IncidentRuleProvider {
    MagicIncident.Severity severityFor(MagicIncident.Type type);
}
