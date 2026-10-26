package com.ou.capstone.ingestion.historical;

import static org.junit.jupiter.api.Assertions.*;

import com.ou.capstone.ingestion.historical.raw.RawFeed;
import com.ou.capstone.ingestion.historical.raw.RawScheduleEntry;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HistoricalJsonReaderTest {
  private final HistoricalJsonReader reader = new HistoricalJsonReader();

  private RawFeed load() throws Exception {
    Path path =
        Path.of(getClass().getResource("/fixtures/historical/sample-schedule.json").toURI());
    return reader.read(path);
  }

  @Test
  void readsTeamsAndConferences() throws Exception {
    RawFeed feed = load();
    assertEquals(3, feed.teams().size());
    assertEquals("Michigan State Spartans", feed.teams().get(0).name());
    assertEquals("Big Ten Conference", feed.teams().get(0).league());
  }

  @Test
  void readsScheduleRowsWithFeedFieldNames() throws Exception {
    RawFeed feed = load();
    RawScheduleEntry game = feed.teams().get(0).schedule().get(1);
    assertEquals("08/29/2025", game.timestamp());
    assertEquals("vs.", game.location());
    assertEquals("Western Michigan Broncos", game.opponent());
    assertEquals("W", game.outcome());
    assertEquals("23", game.pointsScored());
    assertEquals("6", game.pointsAllowed());
  }
}
