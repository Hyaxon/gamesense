package com.ou.capstone.ingestion.historical;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ou.capstone.ingestion.source.SourceFeed;
import java.io.IOException;
import java.nio.file.Path;

/**
 * read Schedule.json off disk and turn it into the raw records, does NOT convert anything into Team
 * or Game, that is the mappers' job
 */
public class HistoricalJsonReader {
  private final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  public SourceFeed read(Path path) throws IOException {
    return mapper.readValue(path.toFile(), SourceFeed.class);
  }
}
