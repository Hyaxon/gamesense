package com.ou.capstone.ingestion.historical;

import com.ou.capstone.data.InMemoryCanonicalDataStore;
import com.ou.capstone.ingestion.IngestionResult;
import com.ou.capstone.ingestion.SourceFeedMapper;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * Historical path: reads a historical schedule file, maps it with the shared mapper, and loads the
 * result into the canonical store.
 */
@Component
public class HistoricalIngestionService {
  private final HistoricalJsonReader reader = new HistoricalJsonReader();
  private final SourceFeedMapper mapper;
  private final InMemoryCanonicalDataStore store;

  public HistoricalIngestionService(SourceFeedMapper mapper, InMemoryCanonicalDataStore store) {
    this.mapper = mapper;
    this.store = store;
  }

  // Loads one historical file. Data already in the store is kept; the result lists skipped records.
  public IngestionResult loadFile(Path path) throws IOException {
    IngestionResult result = mapper.map(reader.read(path));
    store.load(result.teams(), result.games());
    return result;
  }
}
