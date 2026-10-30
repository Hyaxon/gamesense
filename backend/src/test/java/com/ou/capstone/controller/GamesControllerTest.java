package com.ou.capstone.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ou.capstone.data.GameFilter;
import com.ou.capstone.data.InMemoryCanonicalDataStore;
import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.GameStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GamesControllerTest {

  private final RecordingProvider provider = new RecordingProvider();

  private static class RecordingProvider extends InMemoryCanonicalDataStore {
    private GameFilter filter;
    private String gameId;
    private List<Game> results = List.of();
    private Optional<Game> result = Optional.empty();

    @Override
    public synchronized List<Game> findGames(GameFilter filter) {
      this.filter = filter;
      return results;
    }

    @Override
    public synchronized Optional<Game> findGame(String gameId) {
      this.gameId = gameId;
      return result;
    }
  }

  private MockMvc mvc;

  private final Game game =
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

  @BeforeEach
  void setUp() {
    // Exercise HTTP binding and serialization without triggering startup ingestion or live
    // requests.
    mvc = MockMvcBuilders.standaloneSetup(new GamesController(provider)).build();
  }

  @Test
  void listReturnsGamesAsJsonWithNoRestrictions() throws Exception {
    GameFilter filter = new GameFilter(null, null, null, null);
    provider.results = List.of(game);

    mvc.perform(get("/games"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value("game-1"))
        .andExpect(jsonPath("$[0].status").value("FINAL"));
    assertThat(provider.filter).isEqualTo(filter);
  }

  @Test
  void emptyListIsSuccessful() throws Exception {
    provider.results = List.of();

    mvc.perform(get("/games")).andExpect(status().isOk()).andExpect(content().json("[]"));
  }

  @Test
  void combinedParametersReachProviderTogether() throws Exception {
    GameFilter filter = new GameFilter("away", LocalDate.of(2025, 9, 6), GameStatus.FINAL, 2025);
    provider.results = List.of(game);

    mvc.perform(
            get("/games")
                .param("teamId", "away")
                .param("date", "2025-09-06")
                .param("status", "FINAL")
                .param("season", "2025"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("game-1"));
    assertThat(provider.filter).isEqualTo(filter);
  }

  @Test
  void singleFilterLeavesOtherCriteriaUnrestricted() throws Exception {
    GameFilter filter = new GameFilter(null, null, GameStatus.SCHEDULED, null);
    provider.results = List.of();

    mvc.perform(get("/games").param("status", "SCHEDULED"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
    assertThat(provider.filter).isEqualTo(filter);
  }

  @Test
  void knownIdReturnsGame() throws Exception {
    provider.result = Optional.of(game);

    mvc.perform(get("/games/game-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("game-1"))
        .andExpect(jsonPath("$.homeScore").value(21));
    assertThat(provider.gameId).isEqualTo("game-1");
  }

  @Test
  void unknownIdReturnsNotFound() throws Exception {
    provider.result = Optional.empty();

    mvc.perform(get("/games/missing"))
        .andExpect(status().isNotFound())
        .andExpect(content().string(""));
  }

  @ParameterizedTest
  @CsvSource({"date,not-a-date", "date,2025-13-06", "status,UNKNOWN", "season,abc"})
  void invalidParametersReturnBadRequestBeforeQueryingStore(String parameter, String value)
      throws Exception {
    mvc.perform(get("/games").param(parameter, value)).andExpect(status().isBadRequest());
    assertThat(provider.filter).isNull();
    assertThat(provider.gameId).isNull();
  }
}
