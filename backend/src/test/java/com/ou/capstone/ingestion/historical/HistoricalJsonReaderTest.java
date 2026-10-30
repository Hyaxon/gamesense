package com.ou.capstone.ingestion.historical;

import static org.junit.jupiter.api.Assertions.*;

import com.ou.capstone.ingestion.source.SourceFeed;
import com.ou.capstone.ingestion.source.SourceScheduleEntry;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HistoricalJsonReaderTest {
  private final HistoricalJsonReader reader = new HistoricalJsonReader();

  private SourceFeed load() throws Exception {
    Path path =
        Path.of(getClass().getResource("/fixtures/historical/sample-schedule.json").toURI());
    return reader.read(path);
  }

  @Test
  void readsTeamsAndConferences() throws Exception {
    SourceFeed feed = load();
    assertEquals(3, feed.teams().size());
    assertEquals("Michigan State Spartans", feed.teams().get(0).name());
    assertEquals("Big Ten Conference", feed.teams().get(0).league());
  }

  @Test
  void readsScheduleRowsWithFeedFieldNames() throws Exception {
    SourceFeed feed = load();
    SourceScheduleEntry game = feed.teams().get(0).schedule().get(1);
    assertEquals("08/29/2025", game.timestamp());
    assertEquals("vs.", game.location());
    assertEquals("Western Michigan Broncos", game.opponent());
    assertEquals("W", game.outcome());
    assertEquals("23", game.pointsScored());
    assertEquals("6", game.pointsAllowed());
  }
}
