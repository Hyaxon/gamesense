package com.ou.capstone.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ou.capstone.data.InMemoryCanonicalDataStore;
import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.GameStatus;
import com.ou.capstone.domain.Team;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TeamsControllerTest {

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    InMemoryCanonicalDataStore store = new InMemoryCanonicalDataStore();
    Game game =
        new Game(
            "game-1",
            "source-1",
            2025,
            null,
            "home",
            "away",
            null,
            false,
            Instant.parse("2025-09-06T00:00:00Z"),
            GameStatus.FINAL,
            21,
            7,
            "home");
    store.load(
        List.of(
            new Team("home", null, "Oklahoma Sooners", "Oklahoma", "Southeastern Conference", null),
            new Team("away", null, "Texas Longhorns", "Texas", "Southeastern Conference", null),
            new Team("idle", null, "Michigan Wolverines", "Michigan", "Big Ten", null)),
        List.of(game));
    mvc = MockMvcBuilders.standaloneSetup(new TeamsController(store)).build();
  }

  @Test
  void listsAllTeams() throws Exception {
    mvc.perform(get("/teams"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));
  }

  @Test
  void filtersByConference() throws Exception {
    mvc.perform(get("/teams").param("conference", "Big Ten"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value("idle"));
  }

  @Test
  void filtersBySeason() throws Exception {
    mvc.perform(get("/teams").param("season", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void seasonWithNoGamesReturnsEmptyList() throws Exception {
    mvc.perform(get("/teams").param("season", "1999"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void combinesSeasonAndConferenceFilters() throws Exception {
    mvc.perform(get("/teams").param("season", "2025").param("conference", "Big Ten"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void returnsOneTeamById() throws Exception {
    mvc.perform(get("/teams/home"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("home"))
        .andExpect(jsonPath("$.name").value("Oklahoma Sooners"))
        .andExpect(jsonPath("$.logoUrl").doesNotExist());
  }

  @Test
  void unknownTeamReturns404() throws Exception {
    mvc.perform(get("/teams/nope")).andExpect(status().isNotFound());
  }
}
