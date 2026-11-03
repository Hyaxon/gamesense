package com.ou.capstone.ingestion.mapper;

import static org.junit.jupiter.api.Assertions.*;

import com.ou.capstone.domain.Conference;
import com.ou.capstone.ingestion.source.SourceTeam;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConferenceMapperTest {
  private final ConferenceMapper mapper = new ConferenceMapper();

  @Test
  void mapsDistinctConferences() {
    List<SourceTeam> teams =
        List.of(
            new SourceTeam("Michigan State Spartans", "Big Ten Conference", List.of()),
            new SourceTeam("Michigan Wolverines", "Big Ten Conference", List.of()),
            new SourceTeam("Washington State Cougars", "Pacific 12 Conference", List.of()));

    List<Conference> result = mapper.map(teams);

    assertEquals(2, result.size());
    assertEquals("Big Ten Conference", result.get(0).name());
    assertEquals("Pacific 12 Conference", result.get(1).name());
    assertNull(result.get(0).shortName());
  }

  @Test
  void skipsBlankConferences() {
    List<SourceTeam> teams =
        List.of(
            new SourceTeam("Some Team", "", List.of()),
            new SourceTeam("Other Team", null, List.of()),
            new SourceTeam("Michigan State Spartans", "Big Ten Conference", List.of()));

    List<Conference> result = mapper.map(teams);

    assertEquals(1, result.size());
    assertEquals("Big Ten Conference", result.get(0).name());
  }

  @Test
  void emptyListGivesEmptyResult() {
    assertTrue(mapper.map(List.of()).isEmpty());
  }
}
