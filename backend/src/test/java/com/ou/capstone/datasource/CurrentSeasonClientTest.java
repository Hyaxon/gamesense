package com.ou.capstone.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// Verifies CurrentSeasonClient parses the source feed and handles failures, using a mock server so
// the live data source is never called.
class CurrentSeasonClientTest {

  private static final String SOURCE_URL = "http://source.test/api?SPORT=NCAAF&TYPE=JSON";

  private MockRestServiceServer server;
  private CurrentSeasonClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    CurrentSeasonProperties properties =
        new CurrentSeasonProperties(SOURCE_URL, Duration.ofSeconds(5), Duration.ofSeconds(30));
    client = new CurrentSeasonClient(builder.build(), new ObjectMapper(), properties);
  }

  @Test
  void fetchParsesTeamsAndScheduleFields() throws IOException {
    server
        .expect(requestTo(SOURCE_URL))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(loadSample(), MediaType.APPLICATION_JSON));

    SourceFeed feed = client.fetchCurrentSeason();

    assertThat(feed.teams()).hasSize(2);
    SourceTeam team = feed.teams().get(0);
    assertThat(team.name()).isEqualTo("Abilene Christian Wildcats");
    assertThat(team.league()).isEqualTo("United Athletic Conference");

    SourceScheduleEntry playedGame = team.schedule().get(1);
    assertThat(playedGame.timestamp()).isEqualTo("08/29/2026");
    assertThat(playedGame.location()).isEqualTo("@");
    assertThat(playedGame.opponent()).isEqualTo("Lamar Cardinals");
    assertThat(playedGame.outcome()).isEqualTo("L");
    assertThat(playedGame.pointsScored()).isEqualTo("14");
    assertThat(playedGame.pointsAllowed()).isEqualTo("21");
    server.verify();
  }

  @Test
  void fetchKeepsUnplayedGamePlaceholdersAsReceived() throws IOException {
    server
        .expect(requestTo(SOURCE_URL))
        .andRespond(withSuccess(loadSample(), MediaType.APPLICATION_JSON));

    SourceScheduleEntry unplayed = client.fetchCurrentSeason().teams().get(0).schedule().get(2);

    assertThat(unplayed.outcome()).isEqualTo("-");
    assertThat(unplayed.pointsScored()).isEqualTo("0");
  }

  @Test
  void fetchParsesBodyEvenWhenContentTypeIsNotJson() throws IOException {
    server.expect(requestTo(SOURCE_URL)).andRespond(withSuccess(loadSample(), MediaType.TEXT_HTML));

    assertThat(client.fetchCurrentSeason().teams()).hasSize(2);
  }

  @Test
  void fetchDecodesUtf8NamesEvenWhenSourceClaimsLatin1() {
    String body = "{\"teams\":[{\"name\":\"San José State Spartans\",\"league\":\"B\"}]}";
    server
        .expect(requestTo(SOURCE_URL))
        .andRespond(
            withSuccess(
                body.getBytes(StandardCharsets.UTF_8),
                MediaType.parseMediaType("text/html; charset=ISO-8859-1")));

    assertThat(client.fetchCurrentSeason().teams().get(0).name())
        .isEqualTo("San José State Spartans");
  }

  @Test
  void fetchIgnoresUnknownFields() {
    String body = "{\"teams\":[{\"name\":\"A\",\"league\":\"B\",\"schedule\":[],\"extra\":\"x\"}]}";
    server.expect(requestTo(SOURCE_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertThat(client.fetchCurrentSeason().teams().get(0).name()).isEqualTo("A");
  }

  @Test
  void fetchThrowsWhenSourceReturnsServerError() {
    server.expect(requestTo(SOURCE_URL)).andRespond(withServerError());

    assertThatThrownBy(() -> client.fetchCurrentSeason())
        .isInstanceOf(DataSourceException.class)
        .hasMessageContaining("Could not fetch");
  }

  @Test
  void fetchThrowsWhenSourceTimesOut() {
    server
        .expect(requestTo(SOURCE_URL))
        .andRespond(withException(new SocketTimeoutException("Read timed out")));

    assertThatThrownBy(() -> client.fetchCurrentSeason())
        .isInstanceOf(DataSourceException.class)
        .hasMessageContaining("Could not fetch");
  }

  @Test
  void fetchThrowsWhenBodyIsEmpty() {
    server.expect(requestTo(SOURCE_URL)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.fetchCurrentSeason())
        .isInstanceOf(DataSourceException.class)
        .hasMessageContaining("empty");
  }

  @Test
  void fetchThrowsWhenBodyIsNotJson() {
    server
        .expect(requestTo(SOURCE_URL))
        .andRespond(withSuccess("<html>Service down</html>", MediaType.TEXT_HTML));

    assertThatThrownBy(() -> client.fetchCurrentSeason())
        .isInstanceOf(DataSourceException.class)
        .hasMessageContaining("invalid JSON")
        .hasMessageContaining("status 200")
        .hasMessageContaining("text/html")
        .hasMessageContaining("<html>Service down</html>");
  }

  @Test
  void fetchSkipsHtmlStubInFrontOfJson() {
    String body =
        "<!DOCTYPE html> <html lang=\"en\"> <head> <meta charset=\"UTF-8\">"
            + "<title>OU Capstone API</title> </head> </html>\n"
            + "{\"teams\":[{\"name\":\"Air Force Falcons\",\"league\":\"Mountain West Conference\"}]}";
    server
        .expect(requestTo(SOURCE_URL))
        .andRespond(withSuccess(body, MediaType.parseMediaType("text/html;charset=ISO-8859-1")));

    assertThat(client.fetchCurrentSeason().teams().get(0).name()).isEqualTo("Air Force Falcons");
  }

  @Test
  void fetchThrowsWhenTeamsAreMissing() {
    server.expect(requestTo(SOURCE_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.fetchCurrentSeason())
        .isInstanceOf(DataSourceException.class)
        .hasMessageContaining("no teams");
  }

  @Test
  void fetchThrowsWhenTeamsAreEmpty() {
    server
        .expect(requestTo(SOURCE_URL))
        .andRespond(withSuccess("{\"teams\":[]}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.fetchCurrentSeason())
        .isInstanceOf(DataSourceException.class)
        .hasMessageContaining("no teams");
  }

  // Reads the sample feed fixture from src/test/resources.
  private String loadSample() throws IOException {
    return Files.readString(
        Path.of("src/test/resources/datasource/current-season-sample.json"),
        StandardCharsets.UTF_8);
  }
}
