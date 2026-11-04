package com.ou.capstone.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Venue value embedded in a game, with optional city and state. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Venue(String name, String city, String state) {}
