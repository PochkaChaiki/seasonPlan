package com.seasonplan.lifecycle.service;

import java.util.UUID;

public interface CatalogVersionProvider {
    String forPlan(UUID planId);
}
