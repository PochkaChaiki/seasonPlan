package com.seasonplan.reporting.application.port;

import java.util.UUID;

public interface CatalogVersionProvider {
    String forPlan(UUID planId);
}
