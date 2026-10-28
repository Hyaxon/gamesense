package com.ou.capstone.ingestion.mapper;

import static org.junit.jupiter.api.Assertions.*;

import com.ou.capstone.domain.Team;
import com.ou.capstone.ingestion.MalformedRecordException;
import com.ou.capstone.ingestion.source.SourceTeam;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeamMapperTest {
  private final TeamMapper mapper = new TeamMapper();

  @Test
  void mapsValidTeam() {
    Team team =
        mapper.map(new SourceTeam("Michigan State Spartans", "Big Ten Conference", List.of()));
    assertEquals("Michigan State Spartans", team.name());
    assertEquals("Michigan State Spartans", team.externalId());
    assertEquals("Big Ten Conference", team.conference());
    assertNotNull(team.id());
    assertNull(team.shortName());
  }

  @Test
  void missingNameIsMalformed() {
    assertThrows(
        MalformedRecordException.class,
        () -> mapper.map(new SourceTeam("", "Big Ten Conference", List.of())));
  }

  @Test
  void missingConferenceIsMalformed() {
    assertThrows(
        MalformedRecordException.class,
        () -> mapper.map(new SourceTeam("Michigan State Spartans", null, List.of())));
  }
}
