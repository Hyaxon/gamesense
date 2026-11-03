package com.ou.capstone.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ou.capstone.ingestion.historical.HistoricalJsonReader;
import com.ou.capstone.ingestion.source.SourceFeed;
import com.ou.capstone.ingestion.source.SourceScheduleEntry;
import com.ou.capstone.ingestion.source.SourceTeam;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Edge cases for the whole pipeline: missing, malformed, and unusual records. */
class IngestionEdgeCaseTest {
  private final SourceFeedMapper mapper = new SourceFeedMapper();
  private final HistoricalJsonReader reader = new HistoricalJsonReader();

  private static SourceScheduleEntry entry(
      String date,
      String location,
      String opponent,
      String outcome,
      String scored,
      String allowed) {
    return new SourceScheduleEntry("0", date, null, location, opponent, outcome, scored, allowed);
  }

  // two valid teams, with the given row on Michigan State's schedule
  private static SourceFeed twoTeams(SourceScheduleEntry row) {
    return new SourceFeed(
        List.of(
            new SourceTeam("Michigan State", "Big Ten Conference", List.of(row)),
            new SourceTeam("Michigan", "Big Ten Conference", List.of())));
  }

  @Test
  void unknownLocationIsSkipped() {
    IngestionResult result =
        mapper.map(twoTeams(entry("10/25/2025", "X", "Michigan", "L", "20", "31")));

    assertEquals(2, result.teams().size());
    assertTrue(result.games().isEmpty());
    assertEquals(1, result.skipped().size());
  }

  @Test
  void missingOutcomeIsSkipped() {
    IngestionResult result =
        mapper.map(twoTeams(entry("10/25/2025", "vs.", "Michigan", "", "20", "31")));

    assertTrue(result.games().isEmpty());
    assertEquals(1, result.skipped().size());
  }

  @Test
  void negativeScoreIsSkipped() {
    IngestionResult result =
        mapper.map(twoTeams(entry("10/25/2025", "vs.", "Michigan", "L", "-5", "31")));

    assertTrue(result.games().isEmpty());
    assertEquals(1, result.skipped().size());
  }

  @Test
  void missingOpponentOnARealGameIsSkipped() {
    IngestionResult result = mapper.map(twoTeams(entry("10/25/2025", "vs.", "", "L", "20", "31")));

    assertTrue(result.games().isEmpty());
    assertEquals(1, result.skipped().size());
  }

  @Test
  void teamWithNoScheduleStillLoads() {
    SourceFeed feed =
        new SourceFeed(List.of(new SourceTeam("Michigan State", "Big Ten Conference", null)));

    IngestionResult result = mapper.map(feed);

    assertEquals(1, result.teams().size());
    assertEquals(1, result.conferences().size());
    assertTrue(result.games().isEmpty());
    assertTrue(result.skipped().isEmpty());
  }

  @Test
  void emptyFeedGivesEmptyResult() {
    IngestionResult nullTeams = mapper.map(new SourceFeed(null));
    IngestionResult noTeams = mapper.map(new SourceFeed(List.of()));

    assertTrue(nullTeams.teams().isEmpty());
    assertTrue(nullTeams.games().isEmpty());
    assertTrue(noTeams.teams().isEmpty());
    assertTrue(noTeams.skipped().isEmpty());
  }

  @Test
  void duplicateTeamNameIsSkipped() {
    SourceFeed feed =
        new SourceFeed(
            List.of(
                new SourceTeam("Michigan State", "Big Ten Conference", List.of()),
                new SourceTeam("Michigan State", "Big Ten Conference", List.of())));

    IngestionResult result = mapper.map(feed);

    assertEquals(1, result.teams().size());
    assertEquals(1, result.skipped().size());
  }

  @Test
  void extraSpacesInNamesAreTrimmed() {
    SourceFeed feed =
        new SourceFeed(
            List.of(
                new SourceTeam(
                    " Michigan State ",
                    "Big Ten Conference",
                    List.of(entry("10/25/2025", "vs.", " Michigan ", "L", "20", "31"))),
                new SourceTeam("Michigan", "Big Ten Conference", List.of())));

    IngestionResult result = mapper.map(feed);

    assertEquals("Michigan State", result.teams().get(0).name());
    assertEquals(1, result.games().size());
    assertTrue(result.skipped().isEmpty());
  }

  @Test
  void invalidJsonThrowsIoException(@TempDir Path tempDir) throws IOException {
    Path file = tempDir.resolve("bad.json");
    Files.writeString(file, "this is not json");

    assertThrows(IOException.class, () -> reader.read(file));
  }

  @Test
  void missingFileThrowsIoException(@TempDir Path tempDir) {
    Path file = tempDir.resolve("does-not-exist.json");

    assertThrows(IOException.class, () -> reader.read(file));
  }
}
