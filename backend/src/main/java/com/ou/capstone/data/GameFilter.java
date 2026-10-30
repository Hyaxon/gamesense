package com.ou.capstone.data;

import com.ou.capstone.domain.GameStatus;
import java.time.LocalDate;

/**
 * Optional criteria for a game query. Null fields impose no restriction; supplied fields
 * combine with AND. A team matches either side of a matchup. Dates use UTC to match the feed.
 */
public record GameFilter(String teamId, LocalDate date, GameStatus status, Integer season) {}
