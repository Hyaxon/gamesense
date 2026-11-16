package com.ou.capstone.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.ou.capstone.data.GameFilter;
import com.ou.capstone.data.InMemoryCanonicalDataStore;
import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.GameStatus;
import com.ou.capstone.domain.Team;
import com.ou.capstone.prediction.contract.Matchup;
import com.ou.capstone.prediction.contract.PredictionMethod;
import com.ou.capstone.prediction.results.GamePredictions;
import com.ou.capstone.prediction.results.InMemoryPredictionResultStore;
import com.ou.capstone.prediction.results.PredictionResultFixtures;
import com.ou.capstone.prediction.results.PredictionState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GamesControllerTest {

  private final RecordingProvider provider = new RecordingProvider();
  private final InMemoryPredictionResultStore predictions = new InMemoryPredictionResultStore();

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

  // Match Spring Boot's JSON settings, which write timestamps as ISO-8601 strings.
  private MockMvc mvc(RecordingProvider games) {
    return MockMvcBuilders.standaloneSetup(new GamesController(games, predictions))
        .setMessageConverters(
            new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json()
                    .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .build()))
        .build();
  }

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
    provider.load(
        List.of(
            new Team("home", null, "Oklahoma Sooners", "Oklahoma", "Southeastern Conference", null),
            new Team(
                "away",
                null,
                "Texas Longhorns",
                "Texas",
                "Southeastern Conference",
                "https://example.com/texas.png")),
        List.of());
    mvc = mvc(provider);
  }

  @Test
  void listReturnsGamesAsJsonWithNoRestrictions() throws Exception {
    GameFilter filter = new GameFilter(null, null, null, null);
    provider.results = List.of(game);

    mvc.perform(get("/api/games"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value("game-1"))
        .andExpect(jsonPath("$[0].status").value("FINAL"))
        .andExpect(jsonPath("$[0].homeTeam.name").value("Oklahoma Sooners"))
        .andExpect(jsonPath("$[0].awayTeam.name").value("Texas Longhorns"));
    assertThat(provider.filter).isEqualTo(filter);
  }

  @Test
  void emptyListIsSuccessful() throws Exception {
    provider.results = List.of();

    mvc.perform(get("/api/games")).andExpect(status().isOk()).andExpect(content().json("[]"));
  }

  @Test
  void combinedParametersReachProviderTogether() throws Exception {
    GameFilter filter = new GameFilter("away", LocalDate.of(2025, 9, 6), GameStatus.FINAL, 2025);
    provider.results = List.of(game);

    mvc.perform(
            get("/api/games")
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

    mvc.perform(get("/api/games").param("status", "SCHEDULED"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
    assertThat(provider.filter).isEqualTo(filter);
  }

  @Test
  void knownIdReturnsGame() throws Exception {
    provider.result = Optional.of(game);

    mvc.perform(get("/api/games/game-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("game-1"))
        .andExpect(jsonPath("$.homeScore").value(21))
        .andExpect(jsonPath("$.homeTeamId").value("home"))
        .andExpect(jsonPath("$.awayTeamId").value("away"))
        .andExpect(jsonPath("$.homeTeam.id").value("home"))
        .andExpect(jsonPath("$.homeTeam.conference").value("Southeastern Conference"))
        .andExpect(jsonPath("$.homeTeam.logoUrl").doesNotExist())
        .andExpect(jsonPath("$.awayTeam.id").value("away"))
        .andExpect(jsonPath("$.awayTeam.shortName").value("Texas"))
        .andExpect(jsonPath("$.awayTeam.logoUrl").value("https://example.com/texas.png"))
        .andExpect(jsonPath("$.game").doesNotExist())
        .andExpect(jsonPath("$.predictions").doesNotExist());
    assertThat(provider.gameId).isEqualTo("game-1");
  }

  @Test
  void storedPredictionsAreEmbeddedInBothEndpoints() throws Exception {
    Matchup matchup = PredictionResultFixtures.matchup("home", "away");
    predictions.save(
        "game-1",
        GamePredictions.from(
            PredictionResultFixtures.result(
                "game-1",
                matchup,
                PredictionResultFixtures.deterministic(
                    matchup, "elo-v1", "Elo", PredictionMethod.ELO),
                PredictionResultFixtures.failure("glicko2"),
                PredictionResultFixtures.simulation(
                    matchup, "monte-carlo-v1", "Monte Carlo", PredictionMethod.MONTE_CARLO))));
    provider.result = Optional.of(game);
    provider.results = List.of(game);

    mvc.perform(get("/api/games/game-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.predictions.state").value("AVAILABLE"))
        .andExpect(jsonPath("$.predictions.models.length()").value(2))
        .andExpect(jsonPath("$.predictions.models[0].modelId").value("elo-v1"))
        .andExpect(jsonPath("$.predictions.models[0].displayName").value("Elo"))
        .andExpect(jsonPath("$.predictions.models[0].prediction.method").value("ELO"))
        .andExpect(
            jsonPath("$.predictions.models[0].prediction.predictedWinnerTeamId").value("home"))
        .andExpect(jsonPath("$.predictions.models[0].prediction.confidence").value(0.6))
        .andExpect(
            jsonPath("$.predictions.models[0].prediction.generatedAt")
                .value("2026-09-01T12:00:00Z"))
        .andExpect(jsonPath("$.predictions.models[0].simulation").doesNotExist())
        .andExpect(jsonPath("$.predictions.models[1].modelId").value("monte-carlo-v1"))
        .andExpect(jsonPath("$.predictions.models[1].simulation.trials").value(1000))
        .andExpect(jsonPath("$.predictions.aggregate").doesNotExist());
    mvc.perform(get("/api/games"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].predictions.state").value("AVAILABLE"));
  }

  @ParameterizedTest
  @CsvSource({"PENDING", "UNAVAILABLE"})
  void unfinishedPredictionsReturnGameWithStateAndNoModels(PredictionState state) throws Exception {
    predictions.save(
        "game-1",
        state == PredictionState.PENDING
            ? GamePredictions.pending()
            : GamePredictions.unavailable());
    provider.result = Optional.of(game);

    mvc.perform(get("/api/games/game-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("game-1"))
        .andExpect(jsonPath("$.homeTeam.name").value("Oklahoma Sooners"))
        .andExpect(jsonPath("$.predictions.state").value(state.name()))
        .andExpect(jsonPath("$.predictions.models").isEmpty());
  }

  @Test
  void missingTeamsAreOmittedWithoutLosingGameReferences() throws Exception {
    RecordingProvider emptyTeams = new RecordingProvider();
    emptyTeams.result = Optional.of(game);
    emptyTeams.results = List.of(game);
    MockMvc missingTeamsMvc = mvc(emptyTeams);
    missingTeamsMvc
        .perform(get("/api/games/game-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.homeTeamId").value("home"))
        .andExpect(jsonPath("$.awayTeamId").value("away"))
        .andExpect(jsonPath("$.homeTeam").doesNotExist())
        .andExpect(jsonPath("$.awayTeam").doesNotExist());
    missingTeamsMvc
        .perform(get("/api/games"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("game-1"))
        .andExpect(jsonPath("$[0].homeTeam").doesNotExist());
  }

  @Test
  void unknownIdReturnsNotFound() throws Exception {
    provider.result = Optional.empty();

    mvc.perform(get("/api/games/missing"))
        .andExpect(status().isNotFound())
        .andExpect(content().string(""));
  }

  @ParameterizedTest
  @CsvSource({"date,not-a-date", "date,2025-13-06", "status,UNKNOWN", "season,abc"})
  void invalidParametersReturnBadRequestBeforeQueryingStore(String parameter, String value)
      throws Exception {
    mvc.perform(get("/api/games").param(parameter, value)).andExpect(status().isBadRequest());
    assertThat(provider.filter).isNull();
    assertThat(provider.gameId).isNull();
  }
}
