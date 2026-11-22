package com.seasonplan.response.application.port;

import com.seasonplan.response.domain.MagicIncident;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MagicIncidentRepository {
    Optional<MagicIncident> find(UUID id);
    List<MagicIncident> forPlan(UUID planId);
    void save(MagicIncident incident);
}
