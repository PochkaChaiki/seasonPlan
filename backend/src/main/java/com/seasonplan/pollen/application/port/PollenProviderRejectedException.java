package com.seasonplan.pollen.application.port;

import com.seasonplan.shared.domain.DomainValidationException;
import java.util.List;
import java.util.Map;

/** Authoritative provider denial BEFORE mutation, never used for transport or malformed responses. */
public final class PollenProviderRejectedException extends DomainValidationException {
    public PollenProviderRejectedException(Map<String, List<String>> errors) { super(errors); }
}
