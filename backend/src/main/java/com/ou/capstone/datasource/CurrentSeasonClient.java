package com.ou.capstone.datasource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ou.capstone.ingestion.source.SourceFeed;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// Fetches the current-season feed from the external data source and returns it in the source's own
// format. Any failure (unreachable, timeout, bad status, bad JSON) surfaces as a
// DataSourceException.
@Component
public class CurrentSeasonClient {

  private static final int BODY_PREVIEW_LENGTH = 200;

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final CurrentSeasonProperties properties;

  public CurrentSeasonClient(
      RestClient currentSeasonRestClient,
      ObjectMapper objectMapper,
      CurrentSeasonProperties properties) {
    this.restClient = currentSeasonRestClient;
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  // Calls the configured URL and returns the parsed feed, or throws DataSourceException.
  public SourceFeed fetchCurrentSeason() {
    ResponseEntity<byte[]> response = fetchResponse();
    return parseFeed(response);
  }

  // Makes the HTTP request and returns the response, which must have a non-empty body.
  private ResponseEntity<byte[]> fetchResponse() {
    try {
      ResponseEntity<byte[]> response =
          restClient.get().uri(properties.url()).retrieve().toEntity(byte[].class);
      byte[] body = response.getBody();
      if (body == null || body.length == 0) {
        throw new DataSourceException("Data source returned an empty response");
      }
      return response;
    } catch (RestClientException | IllegalArgumentException e) {
      throw new DataSourceException("Could not fetch current-season data: " + e.getMessage(), e);
    }
  }

  // Parses the JSON body into a SourceFeed and checks that it actually contains teams. The source
  // puts a small HTML stub (<!DOCTYPE html>...</html>) in front of the JSON, so parsing starts at
  // the first '{'. The body is read as raw bytes so the JSON parser detects the encoding itself
  // instead of trusting the server's Content-Type header. If parsing fails, the error includes the
  // status, content type and start of the body so it is clear what the server actually sent.
  private SourceFeed parseFeed(ResponseEntity<byte[]> response) {
    byte[] body = response.getBody();
    int jsonStart = findJsonStart(body);
    SourceFeed feed;
    try {
      feed = objectMapper.readValue(body, jsonStart, body.length - jsonStart, SourceFeed.class);
    } catch (IOException e) {
      throw new DataSourceException(
          "Data source returned invalid JSON (status "
              + response.getStatusCode().value()
              + ", content type "
              + response.getHeaders().getContentType()
              + ", body starts with: "
              + previewBody(body)
              + ")",
          e);
    }
    if (feed == null || CollectionUtils.isEmpty(feed.teams())) {
      throw new DataSourceException("Data source response contained no teams");
    }
    return feed;
  }

  // Returns the position of the first '{' in the body, or 0 if there is none.
  private int findJsonStart(byte[] body) {
    for (int i = 0; i < body.length; i++) {
      if (body[i] == '{') {
        return i;
      }
    }
    return 0;
  }

  // Returns the first part of a response body as single-line text, for use in error messages.
  private String previewBody(byte[] body) {
    String text = new String(body, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
    return text.length() > BODY_PREVIEW_LENGTH
        ? text.substring(0, BODY_PREVIEW_LENGTH) + "..."
        : text;
  }
}
