package com.ou.capstone.ingestion.mapper;

import static org.junit.jupiter.api.Assertions.*;

import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.GameStatus;
import com.ou.capstone.domain.Team;
import com.ou.capstone.ingestion.source.SourceScheduleEntry;
import com.ou.capstone.ingestion.source.SourceTeam;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GameMapperTest {
  private final GameMapper mapper = new GameMapper();

  private static Team team(String name) {
    return new Team(name + "-id", name, name, null, "Test Conference", null);
  }

  private static Map<String, Team> teams(String... names) {
    Map<String, Team> map = new HashMap<>();
    for (String name : names) {
      map.put(name, team(name));
    }
    return map;
  }

  private static SourceScheduleEntry row(
      String date,
      String location,
      String opponent,
      String outcome,
      String scored,
      String allowed) {
    return new SourceScheduleEntry("0", date, null, location, opponent, outcome, scored, allowed);
  }

  private static SourceTeam rawTeam(String name, SourceScheduleEntry... rows) {
    return new SourceTeam(name, "Test Conference", List.of(rows));
  }

  @Test
  void mapsHomeGame() {
    var result =
        mapper.map(
            List.of(
                rawTeam("Michigan State", row("10/25/2025", "vs.", "Michigan", "L", "20", "31"))),
            teams("Michigan State", "Michigan"));

    assertEquals(1, result.games().size());
    Game game = result.games().get(0);
    assertEquals("Michigan State-id", game.homeTeamId());
    assertEquals("Michigan-id", game.awayTeamId());
    assertEquals(20, game.homeScore());
    assertEquals(31, game.awayScore());
    assertEquals("Michigan-id", game.winnerTeamId());
    assertEquals(GameStatus.FINAL, game.status());
    assertEquals(2025, game.season());
    assertEquals(Instant.parse("2025-10-25T00:00:00Z"), game.scheduledAt());
    assertFalse(game.isNeutralSite());
    assertNull(game.venue());
    assertNotNull(game.id());
  }

  @Test
  void mapsAwayGameAndSwapsScores() {
    var result =
        mapper.map(
            List.of(
                rawTeam(
                    "Washington State", row("11/01/2025", "@", "Oregon State", "L", "7", "10"))),
            teams("Washington State", "Oregon State"));

    Game game = result.games().get(0);
    assertEquals("Oregon State-id", game.homeTeamId());
    assertEquals("Washington State-id", game.awayTeamId());
    assertEquals(10, game.homeScore());
    assertEquals(7, game.awayScore());
    assertEquals("Oregon State-id", game.winnerTeamId());
  }

  @Test
  void mapsNeutralSiteGame() {
    var result =
        mapper.map(
            List.of(
                rawTeam("Washington State", row("12/22/2025", "N", "Utah State", "W", "34", "21"))),
            teams("Washington State", "Utah State"));

    Game game = result.games().get(0);
    assertTrue(game.isNeutralSite());
    // Utah State comes first alphabetically so it is the "home" side
    assertEquals("Utah State-id", game.homeTeamId());
    assertEquals(21, game.homeScore());
    assertEquals(34, game.awayScore());
    assertEquals("Washington State-id", game.winnerTeamId());
  }

  @Test
  void januaryGameBelongsToPreviousSeason() {
    var result =
        mapper.map(
            List.of(rawTeam("Michigan State", row("01/02/2026", "N", "Michigan", "W", "17", "14"))),
            teams("Michigan State", "Michigan"));

    assertEquals(2025, result.games().get(0).season());
  }

  @Test
  void mirroredRowsMakeOneGame() {
    var result =
        mapper.map(
            List.of(
                rawTeam("Michigan State", row("10/25/2025", "vs.", "Michigan", "L", "20", "31")),
                rawTeam("Michigan", row("10/25/2025", "@", "Michigan State", "W", "31", "20"))),
            teams("Michigan State", "Michigan"));

    assertEquals(1, result.games().size());
    assertTrue(result.skipped().isEmpty());
  }

  @Test
  void neutralMirroredRowsAgreeOnHomeTeam() {
    var result =
        mapper.map(
            List.of(
                rawTeam("Washington State", row("12/22/2025", "N", "Utah State", "W", "34", "21")),
                rawTeam("Utah State", row("12/22/2025", "N", "Washington State", "L", "21", "34"))),
            teams("Washington State", "Utah State"));

    assertEquals(1, result.games().size());
  }

  @Test
  void byeRowsAreIgnored() {
    var result =
        mapper.map(
            List.of(rawTeam("Michigan State", row("08/28/2025", "BYE", "", "", "", ""))),
            teams("Michigan State"));

    assertTrue(result.games().isEmpty());
    assertTrue(result.skipped().isEmpty());
  }

  @Test
  void unplayedGameIsScheduledWithNoScores() {
    var result =
        mapper.map(
            List.of(rawTeam("Michigan State", row("11/22/2026", "vs.", "Michigan", "-", "0", "0"))),
            teams("Michigan State", "Michigan"));

    Game game = result.games().get(0);
    assertEquals(GameStatus.SCHEDULED, game.status());
    assertNull(game.homeScore());
    assertNull(game.awayScore());
    assertNull(game.winnerTeamId());
  }

  @Test
  void tiedGameHasNoWinner() {
    var result =
        mapper.map(
            List.of(
                rawTeam("Michigan State", row("10/25/2025", "vs.", "Michigan", "T", "17", "17"))),
            teams("Michigan State", "Michigan"));

    assertEquals(GameStatus.FINAL, result.games().get(0).status());
    assertNull(result.games().get(0).winnerTeamId());
  }

  @Test
  void opponentNotInFeedIsSkipped() {
    var result =
        mapper.map(
            List.of(
                rawTeam(
                    "Michigan State",
                    row("08/29/2025", "vs.", "Western Michigan", "W", "23", "6"))),
            teams("Michigan State"));

    assertTrue(result.games().isEmpty());
    assertEquals(1, result.skipped().size());
  }

  @Test
  void badScoreIsSkipped() {
    var result =
        mapper.map(
            List.of(
                rawTeam("Michigan State", row("10/25/2025", "vs.", "Michigan", "L", "abc", "31"))),
            teams("Michigan State", "Michigan"));

    assertTrue(result.games().isEmpty());
    assertEquals(1, result.skipped().size());
  }

  @Test
  void badDateIsSkipped() {
    var result =
        mapper.map(
            List.of(
                rawTeam("Michigan State", row("2025-10-25", "vs.", "Michigan", "L", "20", "31"))),
            teams("Michigan State", "Michigan"));

    assertTrue(result.games().isEmpty());
    assertEquals(1, result.skipped().size());
  }
}
