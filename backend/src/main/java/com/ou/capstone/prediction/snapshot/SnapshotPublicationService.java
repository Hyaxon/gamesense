package com.ou.capstone.prediction.snapshot;

import com.ou.capstone.data.InMemoryCanonicalDataStore;
import com.ou.capstone.prediction.client.PythonPredictionClient;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Publishes canonical season data and records only IDs acknowledged by Python. */
@Component
public class SnapshotPublicationService {
  private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotPublicationService.class);
  private final PredictionSnapshotBuilder builder;
  private final PythonPredictionClient client;
  private final InMemoryCanonicalDataStore store;
  private final boolean enabled;

  public SnapshotPublicationService(
      PredictionSnapshotBuilder builder,
      PythonPredictionClient client,
      InMemoryCanonicalDataStore store,
      @Value("${prediction.snapshot.publish.enabled:true}") boolean enabled) {
    this.builder = builder;
    this.client = client;
    this.store = store;
    this.enabled = enabled;
  }

  public void publishSeason(int season) throws IOException {
    // Local file-based setups and tests can keep ingestion without contacting Python.
    if (!enabled) {
      return;
    }
    // Let failures reach the startup callback, which owns logging and continuing startup.
    // Store the ID only after acknowledgement; failure must preserve the previous selection.
    String snapshotId = client.publishSnapshot(builder.build(season));
    store.recordPublishedSnapshot(season, snapshotId);
    LOGGER.info("Published prediction snapshot {} for season {}", snapshotId, season);
  }
}
