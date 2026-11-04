package com.ou.capstone.prediction.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ou.capstone.data.PredictionSnapshot;
import com.ou.capstone.domain.Team;
import com.ou.capstone.prediction.contract.ExecutionKind;
import com.ou.capstone.prediction.contract.Matchup;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks input rejection and valid execution boundaries without a data source or Python service.
 */
class PredictionInputValidatorTest {
  private static final String HOME = "b3b6c2a0-6e2a-4c1e-9d3a-1f2e3d4c5b6a";
  private static final String AWAY = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";
  private static final Matchup MATCHUP = new Matchup(HOME, AWAY, 2025, true);
  private static final PredictionSnapshot SNAPSHOT =
      new PredictionSnapshot("development-2025", 2025);
  private final PredictionInputValidator validator = new PredictionInputValidator();

  @Test
  void acceptsCanonicalDataWithoutRequiringHistoryOrOptionalTeamFields() {
    assertThatCode(() -> validator.validateData(MATCHUP, SNAPSHOT, team(HOME), team(AWAY)))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "bad-id",
        "B3B6C2A0-6E2A-4C1E-9D3A-1F2E3D4C5B6A",
        "b3b6c2a0-6e2a-1c1e-9d3a-1f2e3d4c5b6a",
        "1-1-4000-8000-1"
      })
  void rejectsNoncanonicalTeamIds(String id) {
    reject(
        () -> validator.validateMatchup(new Matchup(id, AWAY, 2025, true)), "/matchup/homeTeamId");
    reject(
        () -> validator.validateMatchup(new Matchup(HOME, id, 2025, true)), "/matchup/awayTeamId");
  }

  @Test
  void rejectsMissingMatchupAndIdenticalTeams() {
    reject(() -> validator.validateMatchup(null), "/matchup");
    reject(
        () -> validator.validateMatchup(new Matchup(HOME, HOME, 2025, false)),
        "/matchup/awayTeamId");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void rejectsInvalidSeason(int season) {
    reject(
        () -> validator.validateMatchup(new Matchup(HOME, AWAY, season, true)), "/matchup/season");
  }

  @Test
  void rejectsUnavailableOrInconsistentResolvedData() {
    reject(() -> validator.validateData(MATCHUP, null, team(HOME), team(AWAY)), "/dataSnapshotId");
    reject(
        () ->
            validator.validateData(
                MATCHUP, new PredictionSnapshot("other-season", 2026), team(HOME), team(AWAY)),
        "/dataSnapshotId");
    reject(
        () -> validator.validateData(MATCHUP, SNAPSHOT, null, team(AWAY)), "/matchup/homeTeamId");
    reject(
        () -> validator.validateData(MATCHUP, SNAPSHOT, team(HOME), null), "/matchup/awayTeamId");
    reject(
        () -> validator.validateData(MATCHUP, SNAPSHOT, team(AWAY), team(AWAY)),
        "/matchup/homeTeamId");
    reject(
        () -> validator.validateData(MATCHUP, SNAPSHOT, team(HOME), team(HOME)),
        "/matchup/awayTeamId");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void rejectsMissingSnapshotId(String id) {
    reject(
        () ->
            validator.validateData(
                MATCHUP, new PredictionSnapshot(id, 2025), team(HOME), team(AWAY)),
        "/dataSnapshotId");
  }

  @ParameterizedTest
  @MethodSource("validSelections")
  void acceptsExecutionKindsAndControlBoundaries(ModelSelection selection) {
    assertThatCode(() -> validator.validateSelections(List.of(selection)))
        .doesNotThrowAnyException();
  }

  static Stream<ModelSelection> validSelections() {
    return Stream.of(
        selection(ExecutionKind.DETERMINISTIC, null, null),
        selection(ExecutionKind.STOCHASTIC, null, null),
        selection(ExecutionKind.STOCHASTIC, 0, null),
        selection(ExecutionKind.STOCHASTIC, Integer.MAX_VALUE, null),
        selection(ExecutionKind.SIMULATION, null, 1),
        selection(ExecutionKind.SIMULATION, 0, 100000),
        selection(ExecutionKind.SIMULATION, Integer.MAX_VALUE, 10));
  }

  @ParameterizedTest
  @MethodSource("invalidSelections")
  void rejectsInvalidControlsEvenAfterAValidSelection(ModelSelection invalid, String field) {
    reject(
        () ->
            validator.validateSelections(
                Arrays.asList(selection(ExecutionKind.DETERMINISTIC, null, null), invalid)),
        "/models/1" + field);
  }

  static Stream<Arguments> invalidSelections() {
    return Stream.of(
        Arguments.of(null, ""),
        Arguments.of(
            new ModelSelection(null, ExecutionKind.DETERMINISTIC, null, null, null), "/modelId"),
        Arguments.of(
            new ModelSelection(" ", ExecutionKind.DETERMINISTIC, null, null, null), "/modelId"),
        Arguments.of(selection(null, null, null), "/executionKind"),
        Arguments.of(selection(ExecutionKind.DETERMINISTIC, 0, null), "/seed"),
        Arguments.of(selection(ExecutionKind.DETERMINISTIC, null, 1), "/trials"),
        Arguments.of(selection(ExecutionKind.STOCHASTIC, -1, null), "/seed"),
        Arguments.of(selection(ExecutionKind.STOCHASTIC, null, 1), "/trials"),
        Arguments.of(selection(ExecutionKind.SIMULATION, -1, 1), "/seed"),
        Arguments.of(selection(ExecutionKind.SIMULATION, null, null), "/trials"),
        Arguments.of(selection(ExecutionKind.SIMULATION, null, 0), "/trials"),
        Arguments.of(selection(ExecutionKind.SIMULATION, null, 100001), "/trials"));
  }

  @Test
  void rejectsMissingOrEmptySelectionList() {
    reject(() -> validator.validateSelections(null), "/models");
    reject(() -> validator.validateSelections(List.of()), "/models");
  }

  @ParameterizedTest
  @ValueSource(strings = {"seed", "trials"})
  void rejectsCommonControlsInsideConfiguration(String control) {
    ModelSelection selection =
        new ModelSelection("test-model", ExecutionKind.SIMULATION, Map.of(control, 1), 42, 10);
    reject(
        () -> validator.validateSelections(List.of(selection)),
        "/models/0/configuration/" + control);
  }

  @Test
  void allowsRepeatedModelsAndLeavesModelSpecificSettingsToPython() {
    ModelSelection selection =
        new ModelSelection(
            "test-model", ExecutionKind.DETERMINISTIC, Map.of("homeAdvantage", 0), null, null);
    assertThatCode(() -> validator.validateSelections(List.of(selection, selection)))
        .doesNotThrowAnyException();
  }

  private static ModelSelection selection(ExecutionKind kind, Integer seed, Integer trials) {
    return new ModelSelection("test-model", kind, null, seed, trials);
  }

  private static Team team(String id) {
    return new Team(id, null, "Example team", null, "Example conference", null);
  }

  private static void reject(Runnable operation, String path) {
    assertThatThrownBy(operation::run)
        .isInstanceOfSatisfying(
            PredictionInputException.class, error -> assertThat(error.getPath()).isEqualTo(path));
  }
}
