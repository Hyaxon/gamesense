package com.ou.capstone.prediction.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.ou.capstone.data.InMemoryCanonicalDataStore;
import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.GameStatus;
import com.ou.capstone.domain.Team;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/** Checks season mapping, game validation, and snapshot wire serialization. */
class PredictionSnapshotBuilderTest {
  private static final String HOME = "b3b6c2a0-6e2a-4c1e-9d3a-1f2e3d4c5b6a";
  private static final String AWAY = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";
  private final InMemoryCanonicalDataStore store = new InMemoryCanonicalDataStore();
  private final PredictionSnapshotBuilder builder = new PredictionSnapshotBuilder(store);

  private static Team team(String id) {
    return new Team(id, null, "Team " + id, null, null, null);
  }

  private static Game game(
      GameStatus status, Integer home, Integer away, String winner, Boolean isNeutralSite) {
    return new Game(
        "c3b6c2a0-6e2a-4c1e-9d3a-1f2e3d4c5b6a",
        "source-game",
        2026,
        1,
        HOME,
        AWAY,
        null,
        isNeutralSite,
        Instant.parse("2026-09-01T20:00:00Z"),
        status,
        home,
        away,
        winner);
  }

  private void load(Game game) {
    store.load(List.of(team(HOME), team(AWAY), team("unused")), List.of(game));
  }

  @ParameterizedTest
  @EnumSource(GameStatus.class)
  void mapsEveryStatusAndOnlyReferencedTeams(GameStatus status) {
    Game game =
        status == GameStatus.FINAL
            ? game(status, 21, 7, HOME, false)
            : game(status, null, null, null, false);
    load(game);
    var request = builder.build(2026);
    assertThat(request.season()).isEqualTo(2026);
    assertThat(request.games()).containsExactly(game);
    assertThat(request.teams()).extracting(Team::id).containsExactly(HOME, AWAY);
    assertThat(request.provenance()).containsEntry("source", "backend-canonical-data-store");
  }

  @ParameterizedTest
  @EnumSource(
      value = GameStatus.class,
      names = {"IN_PROGRESS", "POSTPONED", "CANCELLED"})
  void preservesPartialScores(GameStatus status) {
    Game game = game(status, 7, null, null, null);
    load(game);
    assertThat(builder.build(2026).games()).containsExactly(game);
  }

  @Test
  void acceptsTiesAndAwayWins() {
    load(game(GameStatus.FINAL, 7, 7, null, true));
    assertThat(builder.build(2026).games().get(0).winnerTeamId()).isNull();
    load(game(GameStatus.FINAL, 7, 14, AWAY, false));
    assertThat(builder.build(2026).games().get(0).winnerTeamId()).isEqualTo(AWAY);
  }

  @Test
  void rejectsMalformedFinals() {
    for (Game invalid :
        List.of(
            game(GameStatus.FINAL, null, 7, AWAY, false),
            game(GameStatus.FINAL, 21, 7, AWAY, false),
            game(GameStatus.FINAL, 21, 7, HOME, null),
            game(GameStatus.FINAL, -1, 7, AWAY, false),
            game(GameStatus.FINAL, 7, 7, HOME, false))) {
      load(invalid);
      assertThatThrownBy(() -> builder.build(2026))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(invalid.id());
    }
  }

  @Test
  void rejectsScheduledScoresAndWinners() {
    for (Game invalid :
        List.of(
            game(GameStatus.SCHEDULED, 0, null, null, false),
            game(GameStatus.SCHEDULED, null, null, HOME, false))) {
      load(invalid);
      assertThatThrownBy(() -> builder.build(2026))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Scheduled games");
    }
  }

  @Test
  void rejectsUnknownTeamsAndEmptySeasons() {
    assertThatThrownBy(() -> builder.build(2026)).isInstanceOf(IllegalArgumentException.class);
    store.load(List.of(team(HOME)), List.of(game(GameStatus.SCHEDULED, null, null, null, false)));
    assertThatThrownBy(() -> builder.build(2026))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(AWAY);
    assertThatThrownBy(() -> builder.build(2025)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void serializesWithoutSnapshotIdAndWithUtcInstant() throws Exception {
    load(game(GameStatus.FINAL, 21, 7, HOME, false));
    var mapper =
        Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    var payload = mapper.readTree(mapper.writeValueAsString(builder.build(2026)));
    assertThat(payload.has("dataSnapshotId")).isFalse();
    assertThat(payload.path("games").get(0).path("scheduledAt").asText())
        .isEqualTo("2026-09-01T20:00:00Z");
    assertThat(payload.path("games").get(0).path("status").asText()).isEqualTo("FINAL");
  }
}
