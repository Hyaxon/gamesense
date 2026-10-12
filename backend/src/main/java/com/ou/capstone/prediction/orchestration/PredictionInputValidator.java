package com.ou.capstone.prediction.orchestration;

import com.ou.capstone.data.PredictionSnapshot;
import com.ou.capstone.domain.Team;
import com.ou.capstone.prediction.ExecutionKind;
import com.ou.capstone.prediction.Matchup;
import java.util.List;
import java.util.UUID;

/**
 * Checks canonical prediction inputs and common execution controls without performing lookups.
 *
 * <p>The orchestrator must finish these checks for all selections before making model calls.
 * Providers own snapshot membership; Python owns model availability, capabilities, model-specific
 * configuration, and required history. This validator does not normalize source data.
 */
public final class PredictionInputValidator {

  /** Checks matchup identity and season before data retrieval. */
  public void validateMatchup(Matchup matchup) {
    require(matchup != null, "/matchup", "A matchup is required.");
    validateIdentifier(matchup.homeTeamId(), "/matchup/homeTeamId");
    validateIdentifier(matchup.awayTeamId(), "/matchup/awayTeamId");
    require(
        !matchup.homeTeamId().equals(matchup.awayTeamId()),
        "/matchup/awayTeamId",
        "Home and away teams must differ.");
    require(matchup.season() > 0, "/matchup/season", "Season must be positive.");
  }

  /** Checks that resolved canonical records match the requested matchup and season. */
  public void validateData(
      Matchup matchup, PredictionSnapshot snapshot, Team homeTeam, Team awayTeam) {
    validateMatchup(matchup);
    require(snapshot != null, "/dataSnapshotId", "Prediction snapshot is unavailable.");
    require(
        snapshot.dataSnapshotId() != null && !snapshot.dataSnapshotId().isBlank(),
        "/dataSnapshotId",
        "A snapshot identifier is required.");
    require(
        snapshot.season() == matchup.season(),
        "/dataSnapshotId",
        "Snapshot season must match the matchup season.");
    validateTeam(homeTeam, matchup.homeTeamId(), "/matchup/homeTeamId");
    validateTeam(awayTeam, matchup.awayTeamId(), "/matchup/awayTeamId");
  }

  /** Checks every configured execution, including common seed and trial restrictions. */
  public void validateSelections(List<ModelSelection> selections) {
    require(
        selections != null && !selections.isEmpty(),
        "/models",
        "At least one model selection is required.");
    for (int index = 0; index < selections.size(); index++) {
      ModelSelection selection = selections.get(index);
      String path = "/models/" + index;
      require(selection != null, path, "A model selection is required.");
      require(
          selection.modelId() != null && !selection.modelId().isBlank(),
          path + "/modelId",
          "A model identifier is required.");
      require(
          selection.executionKind() != null,
          path + "/executionKind",
          "An execution kind is required.");
      require(
          selection.seed() == null || selection.seed() >= 0,
          path + "/seed",
          "Seed must be a nonnegative signed 32-bit integer.");
      if (selection.executionKind() == ExecutionKind.DETERMINISTIC) {
        require(
            selection.seed() == null,
            path + "/seed",
            "Deterministic execution cannot specify a seed.");
      }
      if (selection.executionKind() == ExecutionKind.SIMULATION) {
        require(
            selection.trials() != null && selection.trials() >= 1 && selection.trials() <= 100000,
            path + "/trials",
            "Simulation requires between 1 and 100000 trials.");
      } else {
        require(
            selection.trials() == null,
            path + "/trials",
            "Only simulation execution can specify trials.");
      }
      if (selection.configuration() != null) {
        for (String control : List.of("seed", "trials")) {
          require(
              !selection.configuration().containsKey(control),
              path + "/configuration/" + control,
              "Seed and trials belong in the common execution fields.");
        }
      }
    }
  }

  private static void validateTeam(Team team, String expectedId, String path) {
    require(team != null, path, "Required team is unavailable.");
    require(expectedId.equals(team.id()), path, "Resolved team does not match the requested team.");
  }

  private static void validateIdentifier(String value, String path) {
    require(value != null, path, "A canonical lowercase UUID v4 is required.");
    UUID identifier;
    try {
      identifier = UUID.fromString(value);
    } catch (IllegalArgumentException error) {
      throw new PredictionInputException(path, "A canonical lowercase UUID v4 is required.");
    }
    require(
        identifier.version() == 4 && identifier.toString().equals(value),
        path,
        "A canonical lowercase UUID v4 is required.");
  }

  private static void require(boolean condition, String path, String message) {
    if (!condition) {
      throw new PredictionInputException(path, message);
    }
  }
}
