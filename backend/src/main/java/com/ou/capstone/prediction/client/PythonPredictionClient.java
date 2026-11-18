package com.ou.capstone.prediction.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ou.capstone.prediction.contract.ErrorCode;
import com.ou.capstone.prediction.contract.ErrorResponse;
import com.ou.capstone.prediction.contract.ModelExecutionError;
import com.ou.capstone.prediction.contract.ModelExecutionRequest;
import com.ou.capstone.prediction.contract.PredictionServiceResponse;
import com.ou.capstone.prediction.snapshot.SnapshotPublishRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Synchronous HTTP client for model execution and snapshot publication.
 *
 * <p>Posts execution and snapshot requests to Python, validates HTTP metadata, and delegates body
 * validation to the decoder. Model selection and orchestration belong to callers.
 */
public class PythonPredictionClient {
  private final PredictionResponseDecoder decoder = new PredictionResponseDecoder();

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final URI executionUri;
  private final URI snapshotUri;
  private final ObjectWriter snapshotWriter;
  private final Duration requestTimeout;

  public PythonPredictionClient(
      ObjectMapper objectMapper, URI baseUri, Duration connectTimeout, Duration requestTimeout) {
    this.objectMapper = objectMapper;
    this.executionUri = baseUri.resolve("/model-executions");
    this.snapshotUri = baseUri.resolve("/snapshots");
    // Python requires ISO timestamps ending in Z. Copy the mapper so this wire-format
    // requirement does not change date serialization elsewhere in the backend.
    this.snapshotWriter =
        objectMapper
            .copy()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .writerFor(SnapshotPublishRequest.class);
    this.requestTimeout = requestTimeout;
    this.httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
  }

  private static int errorStatus(ErrorCode code) {
    return switch (code) {
      case VALIDATION_ERROR -> 400;
      case NOT_FOUND -> 404;
      case UNSUPPORTED_METHOD -> 422;
      case PREDICTION_FAILED, INTERNAL_ERROR -> 500;
    };
  }

  public PredictionServiceResponse execute(ModelExecutionRequest request) throws IOException {

    HttpResponse<String> response = post(executionUri, objectMapper.writeValueAsString(request));
    PredictionServiceResponse decoded = decoder.decode(response.body(), request);

    int expectedStatus;

    if (decoded instanceof ModelExecutionError modelError) {
      expectedStatus = errorStatus(modelError.error().code());
    } else if (decoded instanceof ErrorResponse error) {
      expectedStatus = errorStatus(error.code());
    } else {
      expectedStatus = 200;
    }

    if (response.statusCode() != expectedStatus) {
      throw new InvalidPredictionResponseException(
          "Prediction service returned an inconsistent HTTP status.");
    }

    return decoded;
  }

  /**
   * Returns Python's ID only after a valid success response. A valid rejection is distinct from a
   * malformed response or transport failure so the caller can report the actual cause.
   */
  public String publishSnapshot(SnapshotPublishRequest request) throws IOException {
    HttpResponse<String> response = post(snapshotUri, snapshotWriter.writeValueAsString(request));
    if (response.statusCode() == 200) {
      // Treat the ID as opaque; Java must not reconstruct it from the season or UUID format.
      return decoder.decodeSnapshotId(response.body());
    }
    ErrorResponse error = decoder.decodeBoundaryError(response.body());
    if (response.statusCode() != errorStatus(error.code())) {
      throw new InvalidPredictionResponseException(
          "Prediction service returned an inconsistent HTTP status.");
    }
    throw new SnapshotPublicationRejectedException(error);
  }

  // Both endpoints share transport policy so publishing has the same timeout, interruption,
  // and content-type guarantees as model execution.
  private HttpResponse<String> post(URI uri, String payload) {

    HttpRequest httpRequest =
        HttpRequest.newBuilder(uri)
            .timeout(requestTimeout)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
            .build();

    HttpResponse<String> response;

    try {
      response =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (HttpTimeoutException e) {
      throw new PredictionTransportException(
          PredictionTransportException.Reason.TIMEOUT, "Prediction service request timed out.", e);
    } catch (IOException e) {
      throw new PredictionTransportException(
          PredictionTransportException.Reason.CONNECTION_FAILURE,
          "Communication with the prediction service failed.",
          e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new PredictionTransportException(
          PredictionTransportException.Reason.INTERRUPTED,
          "Prediction service request was interrupted.",
          e);
    }

    String contentType = response.headers().firstValue("Content-Type").orElse("");

    String mediaType = contentType.split(";", 2)[0].trim();

    if (!mediaType.equalsIgnoreCase("application/json")) {
      throw new InvalidPredictionResponseException(
          "Prediction service returned a non-JSON response.");
    }

    return response;
  }
}
