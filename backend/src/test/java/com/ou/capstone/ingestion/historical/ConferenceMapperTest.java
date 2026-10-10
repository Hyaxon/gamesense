package com.ou.capstone.ingestion.historical;

import com.ou.capstone.domain.Conference;
import com.ou.capstone.ingestion.historical.mapper.ConferenceMapper;
import com.ou.capstone.ingestion.historical.raw.RawTeam;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConferenceMapperTest {
    private final ConferenceMapper mapper = new ConferenceMapper();

    @Test
    void mapsDistinctConferences() {
        List<RawTeam> teams = List.of(
            new RawTeam("Michigan State Spartans", "Big Ten Conference", List.of()),
            new RawTeam("Michigan Wolverines", "Big Ten Conference", List.of()),
            new RawTeam("Washington State Cougars", "Pacific 12 Conference", List.of())
        );

        List<Conference> result = mapper.map(teams);

        assertEquals(2, result.size());
        assertEquals("Big Ten Conference", result.get(0).name());
        assertEquals("Pacific 12 Conference", result.get(1).name());
        assertNull(result.get(0).shortName());
    }

    @Test
    void skipsBlankConferences() {
        List<RawTeam> teams = List.of(
            new RawTeam("Some Team", "", List.of()),
            new RawTeam("Other Team", null, List.of()),
            new RawTeam("Michigan State Spartans", "Big Ten Conference", List.of())
        );

        List<Conference> result = mapper.map(teams);

        assertEquals(1, result.size());
        assertEquals("Big Ten Conference", result.get(0).name());
    }

    @Test
    void emptyListGivesEmptyResult() {
        assertTrue(mapper.map(List.of()).isEmpty());
    }
}