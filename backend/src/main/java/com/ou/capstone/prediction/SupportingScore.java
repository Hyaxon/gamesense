package com.ou.capstone.prediction;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Optional named diagnostic value for a matchup team, with an optional unit.
 *
 * <p>Values may be negative and are not necessarily probabilities or comparable across models.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SupportingScore(String teamId, String name, double value, String unit) {}
