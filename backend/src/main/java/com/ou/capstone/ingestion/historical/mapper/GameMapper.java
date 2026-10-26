package com.ou.capstone.ingestion.historical.mapper;

import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.GameStatus;
import com.ou.capstone.domain.Team;
import com.ou.capstone.ingestion.historical.MalformedRecordException;
import com.ou.capstone.ingestion.historical.raw.RawScheduleEntry;
import com.ou.capstone.ingestion.historical.raw.RawTeam;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** turns the schedule rows into games */
public class GameMapper {
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

  // teamsByName is keyed by the team's full name (that's the externalId in this feed)
  public GameMappingResult map(List<RawTeam> rawTeams, Map<String, Team> teamsByName) {
    List<Game> games = new ArrayList<>();
    List<String> skipped = new ArrayList<>();
    Set<String> seen = new HashSet<>();

    for (RawTeam rawTeam : rawTeams) {
      if (rawTeam.schedule() == null) {
        continue;
      }
      for (RawScheduleEntry entry : rawTeam.schedule()) {
        if (isBye(entry)) {
          continue;
        }
        try {
          Game game = mapEntry(rawTeam, entry, teamsByName, seen);

          // mapEntry returns null when this row is the duplicate of a game we already built
          if (game != null) {
            games.add(game);
          }
        } catch (MalformedRecordException e) {
          skipped.add(e.getMessage());
        }
      }
    }
    return new GameMappingResult(games, skipped);
  }

  // true if this row is a "no game today" row
  private boolean isBye(RawScheduleEntry entry) {
    return "BYE".equalsIgnoreCase(clean(entry.location()));
  }

  // returns null if this game was already built from the other team's row
  private Game mapEntry(
      RawTeam rawTeam, RawScheduleEntry entry, Map<String, Team> teamsByName, Set<String> seen) {
    String teamName = clean(rawTeam.name());
    String opponentName = clean(entry.opponent());
    String where = teamName + " vs " + opponentName + " on " + entry.timestamp();

    if (teamName == null || opponentName == null) {
      throw new MalformedRecordException("Game missing a team name: " + where);
    }
    Team team = teamsByName.get(teamName);
    Team opponent = teamsByName.get(opponentName);
    if (team == null) {
      throw new MalformedRecordException("Team not found: " + where);
    }
    if (opponent == null) {
      throw new MalformedRecordException("Opponent not in feed: " + where);
    }

    LocalDate date = parseDate(entry.timestamp(), where);

    // same key no matter which team's row we are looking at
    boolean teamFirst = teamName.compareTo(opponentName) < 0;
    String key =
        date + "|" + (teamFirst ? teamName + "|" + opponentName : opponentName + "|" + teamName);
    if (seen.contains(key)) {
      return null;
    }

    String location = clean(entry.location());
    boolean neutral = false;
    Team home;
    Team away;
    if ("vs.".equals(location)) {
      home = team;
      away = opponent;
    } else if ("@".equals(location)) {
      home = opponent;
      away = team;
    } else if ("N".equals(location)) {
      // no real home team, so pick one the same way from both rows
      neutral = true;
      home = teamFirst ? team : opponent;
      away = teamFirst ? opponent : team;
    } else {
      throw new MalformedRecordException("Unknown location '" + entry.location() + "': " + where);
    }

    String outcome = clean(entry.outcome());
    if (outcome == null) {
      throw new MalformedRecordException("Game missing outcome: " + where);
    }

    GameStatus status;
    Integer homeScore = null;
    Integer awayScore = null;
    String winnerId = null;
    if ("-".equals(outcome)) {
      // unplayed, the 0 and 0 scores are placeholders
      status = GameStatus.SCHEDULED;
    } else {
      int scored = parseScore(entry.pointsScored(), where);
      int allowed = parseScore(entry.pointsAllowed(), where);
      boolean teamIsHome = home.id().equals(team.id());
      homeScore = teamIsHome ? scored : allowed;
      awayScore = teamIsHome ? allowed : scored;
      status = GameStatus.FINAL;
      if (homeScore > awayScore) {
        winnerId = home.id();
      } else if (awayScore > homeScore) {
        winnerId = away.id();
      }
    }

    // games from January belong to the season that started the year before
    int season = date.getMonthValue() >= 7 ? date.getYear() : date.getYear() - 1;

    seen.add(key);
    return new Game(
        UUID.randomUUID().toString(),
        key,
        season,
        null, // week, not in the feed
        home.id(),
        away.id(),
        null, // venue, not in the feed
        neutral,
        date.atStartOfDay(ZoneOffset.UTC).toInstant(), // no kickoff time in the feed
        status,
        homeScore,
        awayScore,
        winnerId);
  }

  private LocalDate parseDate(String raw, String where) {
    String value = clean(raw);
    if (value == null) {
      throw new MalformedRecordException("Game missing date: " + where);
    }
    try {
      return LocalDate.parse(value, DATE_FORMAT);
    } catch (DateTimeParseException e) {
      throw new MalformedRecordException("Bad date '" + raw + "': " + where);
    }
  }

  private int parseScore(String raw, String where) {
    String value = clean(raw);
    if (value == null) {
      throw new MalformedRecordException("Game missing score: " + where);
    }
    try {
      int score = Integer.parseInt(value);
      if (score < 0) {
        throw new MalformedRecordException("Negative score '" + raw + "': " + where);
      }
      return score;
    } catch (NumberFormatException e) {
      throw new MalformedRecordException("Bad score '" + raw + "': " + where);
    }
  }

  // trims, and turns blank into null
  private String clean(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
