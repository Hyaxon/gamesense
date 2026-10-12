package com.ou.capstone.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Canonical team identity matching the shared Team schema.
 *
 * <p>Relationships use id; externalId preserves source identity and name is a display label.
 * Conference is stored by name. Unavailable optional fields are omitted from JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Team(
    String id,
    String externalId,
    String name,
    String shortName,
    String conference,
    String logoUrl) {}
