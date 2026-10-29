package com.ou.capstone.ingestion.historical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ou.capstone.ingestion.historical.raw.RawFeed;
import com.ou.capstone.ingestion.historical.raw.RawScheduleEntry;
import com.ou.capstone.ingestion.historical.raw.RawTeam;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Edge cases for the whole pipeline: missing, malformed, and unusual records. */
class IngestionEdgeCaseTest {
  private final HistoricalIngestionService service = new HistoricalIngestionService();
  private final HistoricalJsonReader reader = new HistoricalJsonReader();

  private static RawScheduleEntry entry(
      String date,
      String location,
      String opponent,
      String outcome,
      String scored,
      String allowed) {
    return new RawScheduleEntry(date, location, opponent, outcome, scored, allowed);
  }

  // two valid teams, with the given row on Michigan State's schedule
  private static RawFeed twoTeams(RawScheduleEntry row) {
    return new RawFeed(
        List.of(
            new RawTeam("Michigan State", "Big Ten Conference", List.of(row)),
            new RawTeam("Michigan", "Big Ten Conference", List.of())));
  }

  @Test
  void unknownLocationIsSkipped() {
    IngestionResult result =
        service.ingest(twoTeams(entry("10/25/2025", "X", "Michigan", "L", "20", "31")));

    assertEquals(2, result.teams().size());
    assertTrue(result.games().isEmpty());
    assertEquals(1, result.skipped().size());
  }

  @Test
  void missingOutcomeIsSkipped() {
    IngestionResult result =
        service.ingest(twoTeams(entry("10/25/2025", "vs.", "Michigan", "", "20", "31")));

    assertTrue(result.games().isEmpty());
    assertEquals(1, result.skipped().size());
  }

  @Test
  void negativeScoreIsSkipped() {
    IngestionResult result =
        service.ingest(twoTeams(entry("10/25/2025", "vs.", "Michigan", "L", "-5", "31")));

    assertTrue(result.games().isEmpty());
    assertEquals(1, result.skipped().size());
  }

  @Test
  void missingOpponentOnARealGameIsSkipped() {
    IngestionResult result =
        service.ingest(twoTeams(entry("10/25/2025", "vs.", "", "L", "20", "31")));

    assertTrue(result.games().isEmpty());
    assertEquals(1, result.skipped().size());
  }

  @Test
  void teamWithNoScheduleStillLoads() {
    RawFeed feed = new RawFeed(List.of(new RawTeam("Michigan State", "Big Ten Conference", null)));

    IngestionResult result = service.ingest(feed);

    assertEquals(1, result.teams().size());
    assertEquals(1, result.conferences().size());
    assertTrue(result.games().isEmpty());
    assertTrue(result.skipped().isEmpty());
  }

  @Test
  void emptyFeedGivesEmptyResult() {
    IngestionResult nullTeams = service.ingest(new RawFeed(null));
    IngestionResult noTeams = service.ingest(new RawFeed(List.of()));

    assertTrue(nullTeams.teams().isEmpty());
    assertTrue(nullTeams.games().isEmpty());
    assertTrue(noTeams.teams().isEmpty());
    assertTrue(noTeams.skipped().isEmpty());
  }

  @Test
  void duplicateTeamNameIsSkipped() {
    RawFeed feed =
        new RawFeed(
            List.of(
                new RawTeam("Michigan State", "Big Ten Conference", List.of()),
                new RawTeam("Michigan State", "Big Ten Conference", List.of())));

    IngestionResult result = service.ingest(feed);

    assertEquals(1, result.teams().size());
    assertEquals(1, result.skipped().size());
  }

  @Test
  void extraSpacesInNamesAreTrimmed() {
    RawFeed feed =
        new RawFeed(
            List.of(
                new RawTeam(
                    " Michigan State ",
                    "Big Ten Conference",
                    List.of(entry("10/25/2025", "vs.", " Michigan ", "L", "20", "31"))),
                new RawTeam("Michigan", "Big Ten Conference", List.of())));

    IngestionResult result = service.ingest(feed);

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
