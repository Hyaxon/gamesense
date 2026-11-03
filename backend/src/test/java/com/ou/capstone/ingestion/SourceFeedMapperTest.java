package com.ou.capstone.ingestion;

import static org.junit.jupiter.api.Assertions.*;

import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.Team;
import com.ou.capstone.ingestion.historical.HistoricalJsonReader;
import com.ou.capstone.ingestion.source.SourceFeed;
import com.ou.capstone.ingestion.source.SourceScheduleEntry;
import com.ou.capstone.ingestion.source.SourceTeam;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SourceFeedMapperTest {
  private final SourceFeedMapper mapper = new SourceFeedMapper();

  private static SourceScheduleEntry entry(
      String date,
      String location,
      String opponent,
      String outcome,
      String scored,
      String allowed) {
    return new SourceScheduleEntry("0", date, null, location, opponent, outcome, scored, allowed);
  }

  @Test
  void ingestsFixtureFile() throws Exception {
    Path path =
        Path.of(getClass().getResource("/fixtures/historical/sample-schedule.json").toURI());

    IngestionResult result = mapper.map(new HistoricalJsonReader().read(path));

    assertEquals(3, result.teams().size());
    assertEquals(2, result.conferences().size());
    // Western Michigan and Utah State aren't in the fixture, so those 2 games are skipped
    assertEquals(1, result.games().size());
    assertEquals(2, result.skipped().size());

    Set<String> teamIds = result.teams().stream().map(Team::id).collect(Collectors.toSet());
    Game game = result.games().get(0);
    assertTrue(teamIds.contains(game.homeTeamId()));
    assertTrue(teamIds.contains(game.awayTeamId()));
  }

  @Test
  void badTeamIsSkippedAndTheRestStillLoads() {
    SourceFeed feed =
        new SourceFeed(
            List.of(
                new SourceTeam(
                    "Michigan State",
                    "Big Ten Conference",
                    List.of(entry("10/25/2025", "vs.", "Michigan", "L", "20", "31"))),
                new SourceTeam("Michigan", "Big Ten Conference", List.of()),
                new SourceTeam("No Conference U", "", List.of())));

    IngestionResult result = mapper.map(feed);

    assertEquals(2, result.teams().size());
    assertEquals(1, result.conferences().size());
    assertEquals(1, result.games().size());
    assertEquals(1, result.skipped().size());
  }

  @Test
  void processesMultipleSeasons() {
    SourceFeed feed =
        new SourceFeed(
            List.of(
                new SourceTeam(
                    "Michigan State",
                    "Big Ten Conference",
                    List.of(
                        entry("10/25/2024", "vs.", "Michigan", "W", "24", "17"),
                        entry("10/25/2025", "vs.", "Michigan", "L", "20", "31"))),
                new SourceTeam("Michigan", "Big Ten Conference", List.of())));

    IngestionResult result = mapper.map(feed);

    Set<Integer> seasons = result.games().stream().map(Game::season).collect(Collectors.toSet());
    assertEquals(Set.of(2024, 2025), seasons);
  }
}
