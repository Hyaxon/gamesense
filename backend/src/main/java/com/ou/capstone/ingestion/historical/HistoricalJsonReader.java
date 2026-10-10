package com.ou.capstone.ingestion.historical;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ou.capstone.ingestion.historical.raw.RawFeed;

import java.io.IOException;
import java.nio.file.Path;

public class HistoricalJsonReader {
    private final ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public RawFeed read(Path path) throws IOException {
        return mapper.readValue(path.toFile(), RawFeed.class);
    }
}