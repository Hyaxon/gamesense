package com.ou.capstone.datasource;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// Fetches the current-season feed from the external data source and returns it in the source's own
// format. Any failure (unreachable, timeout, bad status, bad JSON) surfaces as a
// DataSourceException.
@Component
public class CurrentSeasonClient {

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
    byte[] body = fetchBody();
    return parseFeed(body);
  }

  // Makes the HTTP request and returns the raw response bytes.
  private byte[] fetchBody() {
    try {
      byte[] body = restClient.get().uri(properties.url()).retrieve().body(byte[].class);
      if (body == null || body.length == 0) {
        throw new DataSourceException("Data source returned an empty response");
      }
      return body;
    } catch (RestClientException | IllegalArgumentException e) {
      throw new DataSourceException("Could not fetch current-season data: " + e.getMessage(), e);
    }
  }

  // Parses the JSON body into a SourceFeed and checks that it actually contains teams. The body is
  // read as raw bytes and parsed here because the source labels its JSON as text/html in
  // ISO-8859-1,
  // which would break Spring's JSON decoding and garble non-ASCII characters if read as text.
  private SourceFeed parseFeed(byte[] body) {
    SourceFeed feed;
    try {
      feed = objectMapper.readValue(body, SourceFeed.class);
    } catch (IOException e) {
      throw new DataSourceException("Data source returned invalid JSON", e);
    }
    if (feed == null || CollectionUtils.isEmpty(feed.teams())) {
      throw new DataSourceException("Data source response contained no teams");
    }
    return feed;
  }
}
