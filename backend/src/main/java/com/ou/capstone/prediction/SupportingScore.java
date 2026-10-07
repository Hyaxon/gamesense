package com.ou.capstone.prediction;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SupportingScore(
        String teamId,
        String name,
        double value,
        String unit) {
}