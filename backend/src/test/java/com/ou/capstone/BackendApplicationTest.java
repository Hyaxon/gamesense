package com.ou.capstone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ou.capstone.data.InMemoryCanonicalDataStore;
import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.GameStatus;
import com.ou.capstone.ingestion.IngestionResult;
import com.ou.capstone.ingestion.SourceFeedMapper;
import com.ou.capstone.ingestion.currentseason.CurrentSeasonIngestionService;
import com.ou.capstone.ingestion.historical.HistoricalIngestionService;
import com.ou.capstone.prediction.snapshot.SnapshotPublicationService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

class BackendApplicationTest {

  private final SnapshotPublicationService publisher = mock(SnapshotPublicationService.class);

  private CurrentSeasonIngestionService currentService() {
    var current = mock(CurrentSeasonIngestionService.class);
    Game game =
        new Game(
            "game",
            null,
            2026,
            null,
            "home",
            "away",
            null,
            false,
            Instant.EPOCH,
            GameStatus.SCHEDULED,
            null,
            null,
            null);
    when(current.loadCurrentSeason())
        .thenReturn(new IngestionResult(List.of(), List.of(), List.of(game), List.of()));
    return current;
  }

  @Test
  void startupLoadsBothSourcesInOrder() throws Exception {
    CurrentSeasonIngestionService current = currentService();
    HistoricalIngestionService historical = mock(HistoricalIngestionService.class);
    new BackendApplication(
            current,
            historical,
            publisher,
            true,
            new ClassPathResource("historical/Schedule-2025.json"))
        .run(new DefaultApplicationArguments());

    InOrder order = inOrder(current, historical, publisher);
    order.verify(current).loadCurrentSeason();
    order.verify(historical).loadStream(any(InputStream.class));
    order.verify(publisher).publishSeason(2026);
    order.verifyNoMoreInteractions();
  }

  @Test
  void disabledIngestionDoesNotLoadEitherSource() throws Exception {
    CurrentSeasonIngestionService current = currentService();
    HistoricalIngestionService historical = mock(HistoricalIngestionService.class);
    new BackendApplication(
            current, historical, publisher, false, new ClassPathResource("missing.json"))
        .run(new DefaultApplicationArguments());

    verifyNoInteractions(current, historical, publisher);
  }

  @Test
  void publishFailureDoesNotBlockStartup() throws Exception {
    var historical = mock(HistoricalIngestionService.class);
    doThrow(new IOException("Python unavailable")).when(publisher).publishSeason(2026);
    new BackendApplication(
            currentService(),
            historical,
            publisher,
            true,
            new ClassPathResource("historical/Schedule-2025.json"))
        .run(new DefaultApplicationArguments());
    verify(historical).loadStream(any(InputStream.class));
    verify(publisher).publishSeason(2026);
  }

  @Test
  void ingestionFailureStillStopsStartup() {
    var current = currentService();
    var historical = mock(HistoricalIngestionService.class);
    when(current.loadCurrentSeason()).thenThrow(new IllegalStateException("Feed failed"));
    assertThatThrownBy(
            () ->
                new BackendApplication(
                        current,
                        historical,
                        publisher,
                        true,
                        new ClassPathResource("historical/Schedule-2025.json"))
                    .run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(historical, publisher);
  }

  @Test
  void historicalFailureStillStopsStartup() {
    var historical = mock(HistoricalIngestionService.class);
    assertThatThrownBy(
            () ->
                new BackendApplication(
                        currentService(),
                        historical,
                        publisher,
                        true,
                        new ClassPathResource("missing.json"))
                    .run(new DefaultApplicationArguments()))
        .isInstanceOf(IOException.class);
    verifyNoInteractions(publisher);
  }

  @Test
  void packagedDatasetLoadsIntoStore() throws Exception {
    InMemoryCanonicalDataStore store = new InMemoryCanonicalDataStore();
    new BackendApplication(
            currentService(),
            new HistoricalIngestionService(new SourceFeedMapper(), store),
            publisher,
            true,
            new ClassPathResource("historical/Schedule-2025.json"))
        .run(new DefaultApplicationArguments());
    assertThat(store.findGames(2025)).isNotEmpty();
  }

  @Test
  void externalDatasetOverridesPackagedDefault(@TempDir Path directory) throws Exception {
    Path file = directory.resolve("custom.json");
    Files.writeString(file, "{\"teams\":[]}");
    InMemoryCanonicalDataStore store = new InMemoryCanonicalDataStore();
    new BackendApplication(
            currentService(),
            new HistoricalIngestionService(new SourceFeedMapper(), store),
            publisher,
            true,
            new FileSystemResource(file))
        .run(new DefaultApplicationArguments());
    assertThat(store.findAllGames()).isEmpty();
  }
}
