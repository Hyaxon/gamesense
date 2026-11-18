package com.ou.capstone.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ou.capstone.prediction.contract.ErrorCode;
import com.ou.capstone.prediction.contract.Matchup;
import com.ou.capstone.prediction.contract.PredictionMethod;
import com.ou.capstone.prediction.contract.SinglePredictionResult;
import com.ou.capstone.prediction.orchestration.PredictionInputException;
import com.ou.capstone.prediction.service.CustomPredictionService;
import com.ou.capstone.prediction.service.PredictionApiException;
import com.ou.capstone.prediction.service.PredictionResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PredictionControllerTest {
  private static final String HOME = "b3b6c2a0-6e2a-4c1e-9d3a-1f2e3d4c5b6a";
  private static final String AWAY = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";
  private static final String BODY =
      "{\"matchup\":{\"homeTeamId\":\""
          + HOME
          + "\",\"awayTeamId\":\""
          + AWAY
          + "\",\"season\":2025,\"isNeutralSite\":true}}";

  private final CustomPredictionService service = mock(CustomPredictionService.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.standaloneSetup(new PredictionController(service)).build();
  }

  private org.springframework.test.web.servlet.ResultActions submit(String body) throws Exception {
    return mvc.perform(
        post("/predictions/custom").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  void returnsPredictionResponseWithoutGameId() throws Exception {
    var matchup = new Matchup(HOME, AWAY, 2025, true);
    var prediction =
        new SinglePredictionResult(
            "p1", PredictionMethod.ELO, HOME, 0.7, Instant.EPOCH, null, null);
    when(service.predict(any()))
        .thenReturn(new PredictionResponse(matchup, List.of(prediction), null));

    submit(BODY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matchup.homeTeamId").value(HOME))
        .andExpect(jsonPath("$.predictions[0].method").value("ELO"))
        .andExpect(jsonPath("$.gameId").doesNotExist())
        .andExpect(jsonPath("$.simulations").doesNotExist());
  }

  @Test
  void mapsApiErrors() throws Exception {
    when(service.predict(any()))
        .thenThrow(
            new PredictionApiException(504, ErrorCode.PREDICTION_FAILED, "Timed out.", null));

    submit(BODY)
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.code").value("PREDICTION_FAILED"));
  }

  @Test
  void unavailableDataIs404AndOtherInputIs400() throws Exception {
    doThrow(new PredictionInputException("/dataSnapshotId", "Prediction snapshot is unavailable."))
        .when(service)
        .predict(any());
    submit(BODY).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));

    doThrow(new PredictionInputException("/matchup/homeTeamId", "A canonical UUID is required."))
        .when(service)
        .predict(any());
    submit(BODY)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[0].path").value("/matchup/homeTeamId"));
  }

  @Test
  void malformedAndUnsupportedValuesAre400() throws Exception {
    submit("{not json").andExpect(status().isBadRequest());
    String unknownMethod = BODY.substring(0, BODY.length() - 1) + ",\"methods\":[\"UNKNOWN\"]}";
    submit(unknownMethod).andExpect(status().isBadRequest());
  }
}
