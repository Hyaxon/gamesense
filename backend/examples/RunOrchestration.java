import com.ou.capstone.BackendApplication;
import com.ou.capstone.data.PredictionDataProvider;
import com.ou.capstone.data.PredictionSnapshot;
import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.GameStatus;
import com.ou.capstone.domain.Team;
import com.ou.capstone.prediction.contract.ErrorCode;
import com.ou.capstone.prediction.contract.Matchup;
import com.ou.capstone.prediction.orchestration.ModelExecutionOutcome;
import com.ou.capstone.prediction.orchestration.PredictionOrchestrationResult;
import com.ou.capstone.prediction.orchestration.PredictionOrchestrator;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Standalone Java 17 source-file demo using canonical synthetic fixtures and real Python HTTP
 * calls. Run from backend/ as documented in README.md. No external data parsing or ingestion is
 * performed.
 */
public class RunOrchestration {
  private static final String HOME = "b3b6c2a0-6e2a-4c1e-9d3a-1f2e3d4c5b6a";
  private static final String AWAY = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";
  private static final String NEW_TEAM = "c3b6c2a0-6e2a-4c1e-9d3a-1f2e3d4c5b6a";
  private static final String GAME_ID = "f3b6c2a0-6e2a-4c1e-9d3a-1f2e3d4c5b6a";
  private static final PredictionSnapshot SNAPSHOT =
      new PredictionSnapshot("development-2025", 2025);

  public static void main(String[] args) {
    // These settings are local to this demo. Two seeded coin-flip executions surround an
    // intentionally unavailable model to demonstrate continuation after an individual failure.
    try (var context =
        new SpringApplicationBuilder(BackendApplication.class)
            .web(WebApplicationType.NONE)
            .initializers(
                application ->
                    application
                        .getBeanFactory()
                        .registerSingleton("demoPredictionDataProvider", new FixtureDataProvider()))
            .run(
                "--prediction.orchestration.enabled=true",
                "--prediction.orchestration.models[0].model-id=coin-flip-v1",
                "--prediction.orchestration.models[0].execution-kind=SIMULATION",
                "--prediction.orchestration.models[0].seed=42",
                "--prediction.orchestration.models[0].trials=10",
                "--prediction.orchestration.models[1].model-id=demo-missing-model",
                "--prediction.orchestration.models[1].execution-kind=DETERMINISTIC",
                "--prediction.orchestration.models[2].model-id=coin-flip-v1",
                "--prediction.orchestration.models[2].execution-kind=SIMULATION",
                "--prediction.orchestration.models[2].seed=42",
                "--prediction.orchestration.models[2].trials=10")) {
      PredictionOrchestrator orchestrator = context.getBean(PredictionOrchestrator.class);
      System.out.println(
          "Synthetic fixture demonstration; these are not real football predictions.");
      var custom = orchestrator.predictMatchup(new Matchup(HOME, AWAY, 2025, true));
      inspect("Custom matchup", custom);
      require(custom.gameId() == null, "Custom matchup must not create a game.");

      var scheduled = orchestrator.predictGame(GAME_ID);
      inspect("Scheduled fixture game", scheduled);
      require(GAME_ID.equals(scheduled.gameId()), "Scheduled game identity must be preserved.");
      require(
          scheduled.matchup().equals(new Matchup(NEW_TEAM, HOME, 2025, false)),
          "Scheduled matchup must match the canonical fixture game.");
      System.out.println(
          "PASS: both entry points retained two successes and one expected model failure.");
    }
  }

  private static void inspect(String label, PredictionOrchestrationResult result) {
    System.out.println(label + " | snapshot=" + result.snapshot().dataSnapshotId());
    require(SNAPSHOT.equals(result.snapshot()), "Expected the development snapshot.");
    require(result.outcomes().size() == 3, "Expected three executions.");
    var executionIds = new HashSet<String>();
    for (int index = 0; index < result.outcomes().size(); index++) {
      ModelExecutionOutcome outcome = result.outcomes().get(index);
      require(executionIds.add(outcome.executionId()), "Execution identifiers must be distinct.");
      if (index == 1) {
        require(
            outcome instanceof ModelExecutionOutcome.Failure, "Expected missing-model failure.");
        var failure = (ModelExecutionOutcome.Failure) outcome;
        require(
            failure.kind() == ModelExecutionOutcome.FailureKind.MODEL_ERROR
                && failure.serviceError().code() == ErrorCode.NOT_FOUND
                && failure.modelId().equals("demo-missing-model"),
            "Expected a correlated NOT_FOUND error for the deliberately missing model.");
        System.out.printf(
            "  %s | %s | %s | execution=%s%n",
            failure.modelId(),
            failure.kind(),
            failure.serviceError().code(),
            failure.executionId());
      } else {
        require(
            outcome instanceof ModelExecutionOutcome.Success,
            "Expected coin-flip success. Check the Python service and snapshot configuration.");
        var success = (ModelExecutionOutcome.Success) outcome;
        var execution = success.result();
        require(success.modelId().equals("coin-flip-v1"), "Expected the coin-flip model.");
        require(
            execution.simulation().trials() == 10
                && Integer.valueOf(42).equals(execution.metadata().seed())
                && Math.abs(execution.simulation().homeWinProbability() - 0.3) < 0.000001
                && Math.abs(execution.simulation().awayWinProbability() - 0.7) < 0.000001
                && execution
                    .prediction()
                    .predictedWinnerTeamId()
                    .equals(result.matchup().awayTeamId())
                && Math.abs(execution.prediction().confidence() - 0.7) < 0.000001,
            "Seeded coin-flip output must match the documented fixture result.");
        System.out.printf(
            "  %s | SUCCESS | home=%.1f away=%.1f | execution=%s%n",
            success.modelId(),
            execution.simulation().homeWinProbability(),
            execution.simulation().awayWinProbability(),
            success.executionId());
      }
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }

  /** Explicit canonical values matching prediction/tests/fixtures/season-2025.json. */
  private static final class FixtureDataProvider implements PredictionDataProvider {
    private final Map<String, Team> teams =
        Map.of(
            HOME, new Team(HOME, null, "Fixture Home", null, "Fixture Conference", null),
            AWAY, new Team(AWAY, null, "Fixture Away", null, "Fixture Conference", null),
            NEW_TEAM,
                new Team(NEW_TEAM, null, "Fixture New Team", null, "Fixture Conference", null));
    private final Game game =
        new Game(
            GAME_ID,
            null,
            2025,
            null,
            NEW_TEAM,
            HOME,
            null,
            false,
            Instant.parse("2025-09-05T00:00:00Z"),
            GameStatus.SCHEDULED,
            null,
            null,
            null);

    @Override
    public Optional<Game> findGame(String gameId) {
      return GAME_ID.equals(gameId) ? Optional.of(game) : Optional.empty();
    }

    @Override
    public Optional<PredictionSnapshot> findSnapshot(int season) {
      return season == SNAPSHOT.season() ? Optional.of(SNAPSHOT) : Optional.empty();
    }

    @Override
    public Optional<Team> findTeam(PredictionSnapshot snapshot, String teamId) {
      return SNAPSHOT.equals(snapshot) ? Optional.ofNullable(teams.get(teamId)) : Optional.empty();
    }
  }
}
