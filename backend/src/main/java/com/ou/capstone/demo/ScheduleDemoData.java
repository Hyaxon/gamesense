package com.ou.capstone.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ou.capstone.data.PredictionDataProvider;
import com.ou.capstone.data.PredictionSnapshot;
import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.GameStatus;
import com.ou.capstone.domain.Team;
import com.ou.capstone.ingestion.historical.HistoricalIngestionService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Local demo artifacts. Ingest once so Java and Python use the same generated identities. */
public record ScheduleDemoData(
    String dataSnapshotId,
    int season,
    Instant cutoff,
    List<Team> teams,
    List<Game> history,
    List<Game> games,
    List<String> skipped)
    implements PredictionDataProvider {

  public ScheduleDemoData {
    teams = List.copyOf(teams);
    history = List.copyOf(history);
    games = List.copyOf(games);
    skipped = List.copyOf(skipped);
  }

  public static ScheduleDemoData ingest(Path source, int season, LocalDate before)
      throws IOException {
    if (season < 1) {
      throw new IllegalArgumentException("Season must be positive.");
    }
    var result = new HistoricalIngestionService().ingest(source);
    Instant cutoff = before.atStartOfDay(ZoneOffset.UTC).toInstant();
    var seasonGames =
        result.games().stream()
            .filter(game -> game.season() == season)
            .sorted(Comparator.comparing(Game::scheduledAt).thenComparing(Game::externalId))
            .toList();
    var history =
        seasonGames.stream()
            .filter(game -> game.status() == GameStatus.FINAL)
            .filter(game -> game.scheduledAt().isBefore(cutoff))
            .toList();
    var games = seasonGames.stream().filter(game -> !game.scheduledAt().isBefore(cutoff)).toList();
    if (history.isEmpty() || games.isEmpty()) {
      throw new IllegalArgumentException(
          "Choose a cutoff with completed history before it and games on or after it.");
    }
    return new ScheduleDemoData(
        "schedule-demo-" + UUID.randomUUID(),
        season,
        cutoff,
        result.teams(),
        history,
        games,
        result.skipped());
  }

  public void write(Path directory, ObjectMapper mapper) throws IOException {
    // Refuse to replace a published snapshot while either service may still have it loaded.
    Files.createDirectory(directory);
    mapper
        .writerWithDefaultPrettyPrinter()
        .writeValue(directory.resolve("dataset.json").toFile(), this);
    mapper
        .writerWithDefaultPrettyPrinter()
        .writeValue(
            directory.resolve("snapshot.json").toFile(),
            Map.of(
                "dataSnapshotId", dataSnapshotId,
                "season", season,
                "provenance", Map.of("source", "Schedule.json", "before", cutoff.toString()),
                "teams", teams,
                "games", history));
  }

  public static ScheduleDemoData read(Path directory, ObjectMapper mapper) throws IOException {
    return mapper.readValue(directory.resolve("dataset.json").toFile(), ScheduleDemoData.class);
  }

  @Override
  public Optional<Game> findGame(String gameId) {
    return games.stream().filter(game -> game.id().equals(gameId)).findFirst();
  }

  @Override
  public Optional<PredictionSnapshot> findSnapshot(int requestedSeason) {
    return requestedSeason == season
        ? Optional.of(new PredictionSnapshot(dataSnapshotId, season))
        : Optional.empty();
  }

  @Override
  public Optional<Team> findTeam(PredictionSnapshot snapshot, String teamId) {
    return findSnapshot(snapshot.season()).filter(snapshot::equals).isPresent()
        ? teams.stream().filter(team -> team.id().equals(teamId)).findFirst()
        : Optional.empty();
  }

  public String teamName(String teamId) {
    return teams.stream()
        .filter(team -> team.id().equals(teamId))
        .map(Team::name)
        .findFirst()
        .orElse(teamId);
  }
}
