package com.ou.capstone.ingestion.historical;

import com.ou.capstone.domain.Team;
import com.ou.capstone.ingestion.historical.mapper.TeamMapper;
import com.ou.capstone.ingestion.historical.raw.RawTeam;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TeamMapperTest {
    private final TeamMapper mapper = new TeamMapper();

    @Test
    void mapsValidTeam() {
        Team team = mapper.map(new RawTeam("Michigan State Spartans", "Big Ten Conference", List.of()));
        assertEquals("Michigan State Spartans", team.name());
        assertEquals("Michigan State Spartans", team.externalId());
        assertEquals("Big Ten Conference", team.conference());
        assertNotNull(team.id());
        assertNull(team.shortName());
    }

    @Test
    void missingNameIsMalformed() {
        assertThrows(MalformedRecordException.class,
            () -> mapper.map(new RawTeam("", "Big Ten Conference", List.of())));
    }

    @Test
    void missingConferenceIsMalformed() {
        assertThrows(MalformedRecordException.class,
            () -> mapper.map(new RawTeam("Michigan State Spartans", null, List.of())));
    }
}