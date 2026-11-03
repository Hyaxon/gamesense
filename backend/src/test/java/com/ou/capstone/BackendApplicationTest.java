package com.ou.capstone;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ou.capstone.ingestion.currentseason.CurrentSeasonIngestionService;
import com.ou.capstone.ingestion.historical.HistoricalIngestionService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.DefaultApplicationArguments;

class BackendApplicationTest {

  @Test
  void startupLoadsBothSourcesInOrder() throws Exception {
    CurrentSeasonIngestionService current = mock(CurrentSeasonIngestionService.class);
    HistoricalIngestionService historical = mock(HistoricalIngestionService.class);
    new BackendApplication(current, historical, true).run(new DefaultApplicationArguments());

    InOrder order = inOrder(current, historical);
    order.verify(current).loadCurrentSeason();
    order.verify(historical).loadFile(Path.of("../data/historical/Schedule-2025.json"));
    order.verifyNoMoreInteractions();
  }

  @Test
  void disabledIngestionDoesNotLoadEitherSource() throws Exception {
    CurrentSeasonIngestionService current = mock(CurrentSeasonIngestionService.class);
    HistoricalIngestionService historical = mock(HistoricalIngestionService.class);
    new BackendApplication(current, historical, false).run(new DefaultApplicationArguments());

    verifyNoInteractions(current, historical);
  }
}
