package com.ou.capstone.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ou.capstone.data.CanonicalDataProvider;
import com.ou.capstone.data.InMemoryCanonicalDataStore;
import com.ou.capstone.data.PredictionSnapshot;
import com.ou.capstone.datasource.CurrentSeasonClient;
import com.ou.capstone.datasource.CurrentSeasonProperties;
import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.GameStatus;
import com.ou.capstone.domain.Team;
import com.ou.capstone.ingestion.currentseason.CurrentSeasonIngestionService;
import com.ou.capstone.ingestion.historical.HistoricalIngestionService;
import com.ou.capstone.ingestion.mapper.CanonicalIds;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// Runs both ingestion paths (historical files and the current-season client) through the shared
// mapper into the central store, then checks everything through the common retrieval interface.
class DataIngestionIntegrationTest {

  private static final String FEED_URL = "http://source.test/feed";
  private static final String HTML_STUB =
      "<!DOCTYPE html> <html lang=\"en\"> <head> <meta charset=\"UTF-8\"> "
          + "<title>OU Capstone API</title> </head> </html>\n";

  private static final String MSU = "Michigan State Spartans";
  private static final String MICH = "Michigan Wolverines";
  private static final String OU = "Oklahoma Sooners";
  private static final String WSU = "Washington State Cougars";
  private static final String MILES = "Miles College Golden Bears";

  private InMemoryCanonicalDataStore store;
  private HistoricalIngestionService historical;
  private CurrentSeasonIngestionService currentSeason;
  private MockRestServiceServer server;

  @BeforeEach
  void setUp() {
    store = new InMemoryCanonicalDataStore();
    SourceFeedMapper mapper = new SourceFeedMapper();
    historical = new HistoricalIngestionService(mapper, store);

    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    CurrentSeasonProperties properties =
        new CurrentSeasonProperties(FEED_URL, Duration.ofSeconds(5), Duration.ofSeconds(30));
    CurrentSeasonClient client =
        new CurrentSeasonClient(builder.build(), new ObjectMapper(), properties);
    currentSeason = new CurrentSeasonIngestionService(client, mapper, store);
  }

  @Test
  void bothPathsLoadAllExpectedGames() throws Exception {
    loadAllThreeSeasons();

    assertThat(store.findGames(2024)).hasSize(2);
    assertThat(store.findGames(2025)).hasSize(2);
    assertThat(store.findGames(2026)).hasSize(3);
    assertThat(store.findAllGames()).hasSize(7);
  }

  @Test
  void currentSeasonPathReportsGamesItCouldNotMap() throws Exception {
    server.expect(requestTo(FEED_URL)).andRespond(feedResponse("current-2026.json"));

    IngestionResult result = currentSeason.loadCurrentSeason();

    // Miles College is not in the feed, so that one game is skipped and reported.
    assertThat(result.skipped()).hasSize(1);
    assertThat(result.skipped().get(0)).contains(MILES);
    assertThat(result.games()).hasSize(3);
  }

  @Test
  void gamesAreRetrievableBySeasonAndId() throws Exception {
    loadAllThreeSeasons();

    for (Game game : store.findGames(2026)) {
      assertThat(store.findGame(game.id())).contains(game);
      assertThat(game.season()).isEqualTo(2026);
    }
    Game played =
        store.findGames(2026).stream()
            .filter(game -> game.status() == GameStatus.FINAL)
            .findFirst()
            .orElseThrow();
    assertThat(played.homeScore()).isEqualTo(34);
    assertThat(played.awayScore()).isEqualTo(14);
    assertThat(played.winnerTeamId()).isEqualTo(CanonicalIds.teamId(MSU));

    List<Game> scheduled =
        store.findGames(2026).stream().filter(g -> g.status() == GameStatus.SCHEDULED).toList();
    assertThat(scheduled).hasSize(2);
    assertThat(scheduled).allSatisfy(game -> assertThat(game.homeScore()).isNull());
    assertThat(store.findGames(1999)).isEmpty();
    assertThat(store.findGame("no-such-id")).isEmpty();
  }

  @Test
  void everyGameHasItsParticipatingTeams() throws Exception {
    loadAllThreeSeasons();

    for (Game game : store.findAllGames()) {
      Team home = store.findTeam(game.homeTeamId()).orElseThrow();
      Team away = store.findTeam(game.awayTeamId()).orElseThrow();
      assertThat(home.id()).isNotEqualTo(away.id());
      assertThat(home.name()).isNotBlank();
    }
  }

  @Test
  void sameTeamKeepsOneCanonicalIdentityAcrossSources() throws Exception {
    loadAllThreeSeasons();

    String msuId = CanonicalIds.teamId(MSU);
    // One game from each of the 2024 file, the 2025 file, and the current-season feed.
    for (int season : List.of(2024, 2025, 2026)) {
      assertThat(store.findGames(season))
          .anyMatch(g -> msuId.equals(g.homeTeamId()) || msuId.equals(g.awayTeamId()));
    }
    Set<String> allTeamIds =
        store.findAllGames().stream()
            .flatMap(game -> java.util.stream.Stream.of(game.homeTeamId(), game.awayTeamId()))
            .collect(Collectors.toSet());
    // Four distinct teams across all three sources, not one set of teams per source.
    assertThat(allTeamIds)
        .containsExactlyInAnyOrder(
            CanonicalIds.teamId(MSU),
            CanonicalIds.teamId(MICH),
            CanonicalIds.teamId(OU),
            CanonicalIds.teamId(WSU));
    assertThat(store.findTeam(msuId).orElseThrow().externalId()).isEqualTo(MSU);
  }

  @Test
  void overlappingGameFromAnotherSourceResolvesToOneCanonicalGame() throws Exception {
    server.expect(requestTo(FEED_URL)).andRespond(feedResponse("current-2026.json"));
    currentSeason.loadCurrentSeason();
    Game before = gameOn(2026, MICH, MSU);
    assertThat(before.status()).isEqualTo(GameStatus.SCHEDULED);

    historical.loadFile(fixture("overlap-2026.json"));

    assertThat(store.findGames(2026)).hasSize(3);
    Game after = gameOn(2026, MICH, MSU);
    assertThat(after.id()).isEqualTo(before.id());
    assertThat(after.status()).isEqualTo(GameStatus.FINAL);
    assertThat(after.homeScore()).isEqualTo(24);
    assertThat(after.awayScore()).isEqualTo(27);
    assertThat(after.winnerTeamId()).isEqualTo(CanonicalIds.teamId(MSU));
  }

  @Test
  void reloadingAnOlderCurrentSeasonFeedDoesNotUndoAFinishedGame() throws Exception {
    server.expect(requestTo(FEED_URL)).andRespond(feedResponse("current-2026.json"));
    currentSeason.loadCurrentSeason();
    historical.loadFile(fixture("overlap-2026.json"));

    server.reset();
    server.expect(requestTo(FEED_URL)).andRespond(feedResponse("current-2026.json"));
    currentSeason.loadCurrentSeason();

    assertThat(store.findGames(2026)).hasSize(3);
    assertThat(gameOn(2026, MICH, MSU).status()).isEqualTo(GameStatus.FINAL);
  }

  @Test
  void loadingAnotherSourcePreservesPreviouslyLoadedSeasons() throws Exception {
    historical.loadFile(fixture("historical-2024.json"));
    List<Game> season2024 = store.findGames(2024);
    assertThat(season2024).hasSize(2);

    historical.loadFile(fixture("historical-2025.json"));
    server.expect(requestTo(FEED_URL)).andRespond(feedResponse("current-2026.json"));
    currentSeason.loadCurrentSeason();

    assertThat(store.findGames(2024)).containsExactlyElementsOf(season2024);
    assertThat(store.findAllGames()).containsAll(season2024);
  }

  @Test
  void snapshotsAndSeasonScopedTeamLookupFollowTheLoadedData() throws Exception {
    loadAllThreeSeasons();

    PredictionSnapshot snapshot2025 = store.findSnapshot(2025).orElseThrow();
    assertThat(snapshot2025.season()).isEqualTo(2025);
    assertThat(snapshot2025.dataSnapshotId()).isNotBlank();
    assertThat(store.findSnapshot(2030)).isEmpty();

    // Oklahoma exists, but did not play in 2025, so it is not part of that season's snapshot.
    String ouId = CanonicalIds.teamId(OU);
    assertThat(store.findTeam(ouId)).isPresent();
    assertThat(store.findTeam(snapshot2025, ouId)).isEmpty();
    assertThat(store.findTeam(snapshot2025, CanonicalIds.teamId(WSU))).isPresent();
    assertThat(store.findTeam(store.findSnapshot(2026).orElseThrow(), ouId)).isPresent();
  }

  @Test
  void downstreamConsumersOnlySeeCanonicalDomainTypes() throws Exception {
    loadAllThreeSeasons();

    Game game = store.findAllGames().get(0);
    assertThat(game).isInstanceOf(com.ou.capstone.domain.Game.class);
    assertThat(store.findTeam(game.homeTeamId()).orElseThrow())
        .isInstanceOf(com.ou.capstone.domain.Team.class);

    // The retrieval API must not mention Source*/Raw* records or the ingestion package.
    for (Class<?> type : List.of(CanonicalDataProvider.class, InMemoryCanonicalDataStore.class)) {
      for (Method method : type.getMethods()) {
        assertThat(method.toGenericString())
            .doesNotContain("com.ou.capstone.ingestion")
            .doesNotContain("SourceFeed")
            .doesNotContain("SourceTeam")
            .doesNotContain("SourceScheduleEntry")
            .doesNotContain("Raw");
      }
    }
  }

  // --- helpers

  private void loadAllThreeSeasons() throws Exception {
    historical.loadFile(fixture("historical-2024.json"));
    historical.loadFile(fixture("historical-2025.json"));
    server.expect(requestTo(FEED_URL)).andRespond(feedResponse("current-2026.json"));
    currentSeason.loadCurrentSeason();
  }

  // The one game in a season between these two teams, whichever of them was home.
  private Game gameOn(int season, String teamOneName, String teamTwoName) {
    String one = CanonicalIds.teamId(teamOneName);
    String two = CanonicalIds.teamId(teamTwoName);
    return store.findGames(season).stream()
        .filter(
            g ->
                (one.equals(g.homeTeamId()) && two.equals(g.awayTeamId()))
                    || (two.equals(g.homeTeamId()) && one.equals(g.awayTeamId())))
        .findFirst()
        .orElseThrow();
  }

  private static Path fixture(String name) throws Exception {
    return Path.of(
        DataIngestionIntegrationTest.class.getResource("/fixtures/ingestion/" + name).toURI());
  }

  // A live-feed style response: the small HTML stub first, then the JSON.
  private static org.springframework.test.web.client.ResponseCreator feedResponse(String name)
      throws Exception {
    String json = Files.readString(fixture(name), StandardCharsets.UTF_8);
    return withSuccess(
        (HTML_STUB + json).getBytes(StandardCharsets.UTF_8),
        MediaType.parseMediaType("text/html;charset=ISO-8859-1"));
  }
}
