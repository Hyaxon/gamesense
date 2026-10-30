package com.ou.capstone.ingestion.currentseason;

import com.ou.capstone.data.InMemoryCanonicalDataStore;
import com.ou.capstone.datasource.CurrentSeasonClient;
import com.ou.capstone.ingestion.IngestionResult;
import com.ou.capstone.ingestion.SourceFeedMapper;
import org.springframework.stereotype.Component;

/**
 * Current-season path: fetches the live feed, maps it with the shared mapper, and loads the result
 * into the canonical store.
 */
@Component
public class CurrentSeasonIngestionService {
  private final CurrentSeasonClient client;
  private final SourceFeedMapper mapper;
  private final InMemoryCanonicalDataStore store;

  public CurrentSeasonIngestionService(
      CurrentSeasonClient client, SourceFeedMapper mapper, InMemoryCanonicalDataStore store) {
    this.client = client;
    this.mapper = mapper;
    this.store = store;
  }

  // Fetches and loads the current season. Throws DataSourceException if the feed is unusable, in
  // which case the store is left unchanged. The result lists skipped records.
  public IngestionResult loadCurrentSeason() {
    IngestionResult result = mapper.map(client.fetchCurrentSeason());
    store.load(result.teams(), result.games());
    return result;
  }
}
