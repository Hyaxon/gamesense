package com.ou.capstone.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PythonPredictionClientTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final AtomicReference<CapturedRequest> captured = new AtomicReference<>();
  private HttpServer server;
  private URI baseUri;

  private record CapturedRequest(
      String method, String path, String contentType, String accept, String body) {}

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private PythonPredictionClient client(Duration timeout) {
    return new PythonPredictionClient(mapper, baseUri, Duration.ofSeconds(1), timeout);
  }

  private PythonPredictionClient client() {
    return client(Duration.ofSeconds(5));
  }

  private void reply(int status, String contentType, String body) {
    server.createContext(
        "/",
        exchange -> {
          try (exchange) {
            captured.set(
                new CapturedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestHeaders().getFirst("Accept"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            if (contentType != null) {
              exchange.getResponseHeaders().set("Content-Type", contentType);
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length != 0) {
              exchange.getResponseBody().write(bytes);
            }
          }
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"deterministic", "stochastic", "simulation"})
  void postsContractRequestAndParsesSuccess(String example) throws Exception {
    reply(200, "application/json; charset=utf-8", fixture(example, "result"));
    ModelExecutionRequest request = request(example);
    ModelExecutionResult response = (ModelExecutionResult) client().execute(request);
    assertThat(response.executionId()).isEqualTo(request.executionId());
    assertThat(response.model().modelId()).isEqualTo(request.modelId());
    assertThat(response.metadata().executionKind()).isEqualTo(request.executionKind());
    CapturedRequest sent = captured.get();
    assertThat(sent.method()).isEqualTo("POST");
    assertThat(sent.path()).isEqualTo("/model-executions");
    assertThat(sent.contentType()).isEqualTo("application/json");
    assertThat(sent.accept()).isEqualTo("application/json");
    assertThat(mapper.readTree(sent.body()))
        .isEqualTo(mapper.readTree(fixture(example, "request")));
  }

  @ParameterizedTest
  @CsvSource({
    "VALIDATION_ERROR,400",
    "NOT_FOUND,404",
    "UNSUPPORTED_METHOD,422",
    "PREDICTION_FAILED,500",
    "INTERNAL_ERROR,500"
  })
  void preservesCorrelatedModelErrors(ErrorCode code, int status) throws Exception {
    ObjectNode error =
        mapper.createObjectNode().put("code", code.name()).put("message", "Failure.");
    ModelExecutionRequest request = request("deterministic");
    ObjectNode body =
        mapper
            .createObjectNode()
            .put("status", "ERROR")
            .put("executionId", request.executionId())
            .put("modelId", request.modelId());
    body.set("error", error);
    reply(status, "application/json", body.toString());
    ModelExecutionError response = (ModelExecutionError) client().execute(request);
    assertThat(response.executionId()).isEqualTo(request.executionId());
    assertThat(response.modelId()).isEqualTo(request.modelId());
    assertThat(response.error().code()).isEqualTo(code);
  }

  @Test
  void preservesPlainBoundaryError() throws Exception {
    reply(
        400,
        "application/json",
        "{\"code\":\"VALIDATION_ERROR\",\"message\":\"Invalid request.\"}");
    ErrorResponse response = (ErrorResponse) client().execute(request("deterministic"));
    assertThat(response.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "<html>error</html>", "{", "{}"})
  void rejectsMalformedResponse(String body) throws Exception {
    reply(200, "application/json", body);
    ModelExecutionRequest request = request("deterministic");
    assertThatThrownBy(() -> client().execute(request))
        .isInstanceOf(InvalidPredictionResponseException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"text/html", "text/plain", ""})
  void rejectsWrongOrMissingContentType(String type) throws Exception {
    reply(200, type.isEmpty() ? null : type, fixture("deterministic", "result"));
    ModelExecutionRequest request = request("deterministic");
    assertThatThrownBy(() -> client().execute(request))
        .isInstanceOf(InvalidPredictionResponseException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 500, 302})
  void rejectsSuccessWithWrongHttpStatus(int status) throws Exception {
    reply(status, "application/json", fixture("deterministic", "result"));
    ModelExecutionRequest request = request("deterministic");
    assertThatThrownBy(() -> client().execute(request))
        .isInstanceOf(InvalidPredictionResponseException.class);
  }

  @Test
  void rejectsErrorWithSuccessHttpStatus() throws Exception {
    reply(
        200,
        "application/json",
        "{\"code\":\"VALIDATION_ERROR\",\"message\":\"Invalid request.\"}");
    ModelExecutionRequest request = request("deterministic");
    assertThatThrownBy(() -> client().execute(request))
        .isInstanceOf(InvalidPredictionResponseException.class);
  }

  @Test
  void doesNotAttachAnotherExecutionsResult() throws Exception {
    ObjectNode body = (ObjectNode) mapper.readTree(fixture("deterministic", "result"));
    body.put("executionId", "b1b2c3d4-e5f6-4789-8abc-1234567890ab");
    reply(200, "application/json", body.toString());
    ModelExecutionRequest request = request("deterministic");
    assertThatThrownBy(() -> client().execute(request))
        .isInstanceOf(InvalidPredictionResponseException.class);
  }

  @Test
  void reportsUnavailableService() throws Exception {
    server.stop(0);
    ModelExecutionRequest request = request("deterministic");
    assertThatThrownBy(() -> client().execute(request))
        .isInstanceOfSatisfying(
            PredictionTransportException.class,
            error ->
                assertThat(error.reason())
                    .isEqualTo(PredictionTransportException.Reason.CONNECTION_FAILURE));
  }

  @Test
  void reportsTimeoutWhenServerDoesNotRespond() throws Exception {
    CountDownLatch received = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    server.createContext(
        "/",
        exchange -> {
          try (exchange) {
            received.countDown();
            try {
              release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
              Thread.currentThread().interrupt();
            }
          }
        });
    ModelExecutionRequest request = request("deterministic");
    try {
      assertThatThrownBy(() -> client(Duration.ofMillis(500)).execute(request))
          .isInstanceOfSatisfying(
              PredictionTransportException.class,
              error ->
                  assertThat(error.reason())
                      .isEqualTo(PredictionTransportException.Reason.TIMEOUT));
      assertThat(received.getCount()).isZero();
    } finally {
      release.countDown();
    }
  }

  @Test
  void restoresInterruptFlag() throws Exception {
    ModelExecutionRequest request = request("deterministic");
    PythonPredictionClient client = client();
    try {
      Thread.currentThread().interrupt();
      assertThatThrownBy(() -> client.execute(request))
          .isInstanceOfSatisfying(
              PredictionTransportException.class,
              error ->
                  assertThat(error.reason())
                      .isEqualTo(PredictionTransportException.Reason.INTERRUPTED));
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  private ModelExecutionRequest request(String name) throws Exception {
    return mapper.readValue(fixture(name, "request"), ModelExecutionRequest.class);
  }

  private String fixture(String name, String suffix) throws Exception {
    return Files.readString(
        Path.of("../shared/examples/model-execution", name + "-" + suffix + ".json"));
  }
}
