package com.ou.capstone.ingestion.historical;

import com.ou.capstone.domain.Conference;
import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.Team;
import java.util.List;

// everything that loaded, plus a reason for every record that got skipped
/**
 * The final output of the whole pipeline: canonical Teams, Conferences, and Games, plus a message
 * for every record that got skipped
 */
public record IngestionResult(
    List<Team> teams, List<Conference> conferences, List<Game> games, List<String> skipped) {}
