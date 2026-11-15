package com.seasonplan.lifecycle.service;

import com.seasonplan.lifecycle.domain.MagicIncident;

public interface IncidentRuleProvider {
    MagicIncident.Severity severityFor(MagicIncident.Type type);
}
