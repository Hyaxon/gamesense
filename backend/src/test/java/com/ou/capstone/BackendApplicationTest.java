package com.ou.capstone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ou.capstone.data.InMemoryCanonicalDataStore;
import com.ou.capstone.ingestion.SourceFeedMapper;
import com.ou.capstone.ingestion.currentseason.CurrentSeasonIngestionService;
import com.ou.capstone.ingestion.historical.HistoricalIngestionService;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

class BackendApplicationTest {

  @Test
  void startupLoadsBothSourcesInOrder() throws Exception {
    CurrentSeasonIngestionService current = mock(CurrentSeasonIngestionService.class);
    HistoricalIngestionService historical = mock(HistoricalIngestionService.class);
    new BackendApplication(
            current, historical, true, new ClassPathResource("historical/Schedule-2025.json"))
        .run(new DefaultApplicationArguments());

    InOrder order = inOrder(current, historical);
    order.verify(current).loadCurrentSeason();
    order.verify(historical).loadStream(any(InputStream.class));
    order.verifyNoMoreInteractions();
  }

  @Test
  void disabledIngestionDoesNotLoadEitherSource() throws Exception {
    CurrentSeasonIngestionService current = mock(CurrentSeasonIngestionService.class);
    HistoricalIngestionService historical = mock(HistoricalIngestionService.class);
    new BackendApplication(current, historical, false, new ClassPathResource("missing.json"))
        .run(new DefaultApplicationArguments());

    verifyNoInteractions(current, historical);
  }

  @Test
  void packagedDatasetLoadsIntoStore() throws Exception {
    InMemoryCanonicalDataStore store = new InMemoryCanonicalDataStore();
    new BackendApplication(
            mock(CurrentSeasonIngestionService.class),
            new HistoricalIngestionService(new SourceFeedMapper(), store),
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
            mock(CurrentSeasonIngestionService.class),
            new HistoricalIngestionService(new SourceFeedMapper(), store),
            true,
            new FileSystemResource(file))
        .run(new DefaultApplicationArguments());
    assertThat(store.findAllGames()).isEmpty();
  }
}
