package com.ou.capstone.prediction;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class PythonPredictionClient {
  private final PredictionResponseDecoder decoder = new PredictionResponseDecoder();

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final URI executionUri;
  private final Duration requestTimeout;

  public PythonPredictionClient(
      ObjectMapper objectMapper, URI baseUri, Duration connectTimeout, Duration requestTimeout) {
    this.objectMapper = objectMapper;
    this.executionUri = baseUri.resolve("/model-executions");
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

    String payload = objectMapper.writeValueAsString(request);

    HttpRequest httpRequest =
        HttpRequest.newBuilder(executionUri)
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
}
