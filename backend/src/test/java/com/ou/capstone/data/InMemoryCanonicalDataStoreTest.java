package com.ou.capstone.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.GameStatus;
import com.ou.capstone.domain.Team;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

// Verifies how the store merges loaded records and scopes its lookups.
class InMemoryCanonicalDataStoreTest {

  private final InMemoryCanonicalDataStore store = new InMemoryCanonicalDataStore();

  private static Team team(String id, String conference) {
    return new Team(id, id, id, null, conference, null);
  }

  private static Game game(String id, int season, GameStatus status, Integer home, Integer away) {
    return new Game(
        id, id, season, null, "home", "away", null, false, Instant.EPOCH, status, home, away, null);
  }

  @Test
  void emptyStoreReturnsEmptyResults() {
    assertThat(store.findAllGames()).isEmpty();
    assertThat(store.findGames(2025)).isEmpty();
    assertThat(store.findGame("x")).isEmpty();
    assertThat(store.findTeam("x")).isEmpty();
    assertThat(store.findSnapshot(2025)).isEmpty();
  }

  @Test
  void loadingMoreDataKeepsWhatWasAlreadyLoaded() {
    store.load(List.of(team("home", "A")), List.of(game("g1", 2024, GameStatus.FINAL, 1, 0)));
    store.load(List.of(team("away", "B")), List.of(game("g2", 2025, GameStatus.FINAL, 2, 1)));

    assertThat(store.findAllGames()).extracting(Game::id).containsExactly("g1", "g2");
    assertThat(store.findTeam("home")).isPresent();
    assertThat(store.findTeam("away")).isPresent();
  }

  @Test
  void sameIdIsStoredOnceAndTeamsAreReplaced() {
    store.load(List.of(team("home", "Old Conference")), List.of());
    store.load(List.of(team("home", "New Conference")), List.of());

    assertThat(store.findTeam("home").orElseThrow().conference()).isEqualTo("New Conference");
  }

  @Test
  void finishedGameReplacesScheduledGame() {
    store.load(List.of(), List.of(game("g1", 2026, GameStatus.SCHEDULED, null, null)));
    store.load(List.of(), List.of(game("g1", 2026, GameStatus.FINAL, 3, 1)));

    assertThat(store.findGames(2026)).hasSize(1);
    assertThat(store.findGame("g1").orElseThrow().status()).isEqualTo(GameStatus.FINAL);
  }

  @Test
  void finishedGameIsNotReplacedByScheduledGame() {
    store.load(List.of(), List.of(game("g1", 2026, GameStatus.FINAL, 3, 1)));
    store.load(List.of(), List.of(game("g1", 2026, GameStatus.SCHEDULED, null, null)));

    Game stored = store.findGame("g1").orElseThrow();
    assertThat(stored.status()).isEqualTo(GameStatus.FINAL);
    assertThat(stored.homeScore()).isEqualTo(3);
  }

  @Test
  void snapshotAndTeamLookupAreScopedToTheSeason() {
    store.load(
        List.of(team("home", "A"), team("away", "B"), team("idle", "C")),
        List.of(game("g1", 2025, GameStatus.FINAL, 1, 0)));

    PredictionSnapshot snapshot = store.findSnapshot(2025).orElseThrow();
    assertThat(store.findTeam(snapshot, "home")).isPresent();
    assertThat(store.findTeam(snapshot, "away")).isPresent();
    assertThat(store.findTeam(snapshot, "idle")).isEmpty();
    assertThat(store.findSnapshot(2024)).isEmpty();
  }

  @Test
  void optionalFiltersMatchEitherTeamAndCombineWithAnd() {
    Game finalGame = game("final", 2025, GameStatus.FINAL, 3, 1);
    Game scheduled = game("scheduled", 2026, GameStatus.SCHEDULED, null, null);
    store.load(List.of(), List.of(finalGame, scheduled));

    assertThat(store.findGames(new GameFilter(null, null, null, null)))
        .containsExactly(finalGame, scheduled);
    for (String teamId : List.of("home", "away")) {
      assertThat(store.findGames(new GameFilter(teamId, null, null, null)))
          .containsExactly(finalGame, scheduled);
    }
    assertThat(store.findGames(new GameFilter("missing", null, null, null))).isEmpty();
    assertThat(store.findGames(new GameFilter(null, null, GameStatus.FINAL, null)))
        .containsExactly(finalGame);
    assertThat(store.findGames(new GameFilter(null, null, null, 2026)))
        .containsExactly(scheduled);
    assertThat(store.findGames(
        new GameFilter("away", LocalDate.of(1970, 1, 1), GameStatus.FINAL, 2025)))
        .containsExactly(finalGame);
    assertThat(store.findGames(new GameFilter(null, null, GameStatus.FINAL, 2026))).isEmpty();
  }

  @Test
  void dateFilterUsesUtcCalendarDayAndExcludesMissingDates() {
    Game late = new Game(
        "late", "late", 2025, null, "home", "away", null, false,
        Instant.parse("2025-09-06T23:59:59Z"), GameStatus.SCHEDULED, null, null, null);
    Game nextDay = new Game(
        "next", "next", 2025, null, "home", "away", null, false,
        Instant.parse("2025-09-07T00:00:00Z"), GameStatus.SCHEDULED, null, null, null);
    Game undated = new Game(
        "undated", "undated", 2025, null, "home", "away", null, false,
        null, GameStatus.SCHEDULED, null, null, null);
    store.load(List.of(), List.of(late, nextDay, undated));

    assertThat(store.findGames(new GameFilter(null, LocalDate.of(2025, 9, 6), null, null)))
        .containsExactly(late);
    assertThat(store.findGames(new GameFilter(null, LocalDate.of(2025, 9, 7), null, null)))
        .containsExactly(nextDay);
    assertThat(store.findGames(new GameFilter(null, null, null, null)))
        .containsExactly(late, nextDay, undated);
  }

}
