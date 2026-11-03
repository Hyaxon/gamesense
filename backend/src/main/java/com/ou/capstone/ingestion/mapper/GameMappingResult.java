package com.ou.capstone.ingestion.mapper;

import com.ou.capstone.domain.Game;
import java.util.List;

// games that mapped fine, plus a reason for every row that got skipped
/**
 * what GameMapper gives back, the games that mapped fine, plus a message for every row it had to
 * skip
 */
public record GameMappingResult(List<Game> games, List<String> skipped) {}
