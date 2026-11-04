package com.ou.capstone.prediction.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ou.capstone.data.PredictionDataProvider;
import com.ou.capstone.data.PredictionSnapshot;
import com.ou.capstone.domain.Game;
import com.ou.capstone.domain.Team;
import com.ou.capstone.prediction.client.PredictionServiceConfiguration;
import com.ou.capstone.prediction.client.PythonPredictionClient;
import com.ou.capstone.prediction.contract.ExecutionKind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

/** Verifies opt-in wiring and typed model settings without a live source or prediction service. */
class PredictionOrchestrationConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
          .withUserConfiguration(
              PredictionServiceConfiguration.class, PredictionOrchestrationConfiguration.class)
          .withPropertyValues(
              "prediction.service.base-url=http://127.0.0.1:1",
              "prediction.service.connect-timeout=1s",
              "prediction.service.request-timeout=1s");

  private ApplicationContextRunner configured() {
    return runner
        .withBean(PredictionDataProvider.class, UnusedDataProvider::new)
        .withPropertyValues(
            "prediction.orchestration.enabled=true",
            "prediction.orchestration.models[0].model-id=coin-flip-v1",
            "prediction.orchestration.models[0].execution-kind=SIMULATION",
            "prediction.orchestration.models[0].trials=1000",
            "prediction.orchestration.models[0].seed=42");
  }

  @Test
  void staysDisabledWithoutProviderOrSelections() {
    runner.run(
        context ->
            assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(PredictionOrchestrator.class)
                .hasSingleBean(PythonPredictionClient.class));
    runner
        .withPropertyValues("prediction.orchestration.enabled=false")
        .run(
            context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(PredictionOrchestrator.class));
  }

  @Test
  void bindsOrderedModelsAndCreatesReusableOrchestratorWithoutExecuting() {
    configured()
        .withPropertyValues(
            "prediction.orchestration.models[1].model-id=coin-flip-v1",
            "prediction.orchestration.models[1].execution-kind=SIMULATION",
            "prediction.orchestration.models[1].trials=10")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(PredictionOrchestrator.class);
              assertThat(context.getBean(PredictionOrchestrator.class))
                  .isSameAs(context.getBean(PredictionOrchestrator.class));
              List<ModelSelection> selections =
                  context
                      .getBean(PredictionOrchestrationConfiguration.Properties.class)
                      .toSelections();
              assertThat(selections)
                  .containsExactly(
                      new ModelSelection("coin-flip-v1", ExecutionKind.SIMULATION, null, 42, 1000),
                      new ModelSelection("coin-flip-v1", ExecutionKind.SIMULATION, null, null, 10));
            });
  }

  @Test
  void preservesJsonConfigurationTypes() {
    configured()
        .withPropertyValues(
            "prediction.orchestration.models[0].configuration-json={\"count\":2,\"factor\":0.5,\"enabled\":true,\"labels\":[\"a\"],\"nested\":{\"value\":3},\"optional\":null}")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              Map<String, Object> configuration =
                  context
                      .getBean(PredictionOrchestrationConfiguration.Properties.class)
                      .toSelections()
                      .get(0)
                      .configuration();
              assertThat(configuration)
                  .containsEntry("count", 2)
                  .containsEntry("factor", 0.5)
                  .containsEntry("enabled", true)
                  .containsEntry("labels", List.of("a"))
                  .containsEntry("nested", Map.of("value", 3))
                  .containsEntry("optional", null);
            });
  }

  @Test
  void requiresProviderWhenEnabled() {
    runner
        .withPropertyValues(
            "prediction.orchestration.enabled=true",
            "prediction.orchestration.models[0].model-id=coin-flip-v1",
            "prediction.orchestration.models[0].execution-kind=SIMULATION",
            "prediction.orchestration.models[0].trials=10")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class);
            });
  }

  @Test
  void requiresSelectionsWhenEnabled() {
    runner
        .withBean(PredictionDataProvider.class, UnusedDataProvider::new)
        .withPropertyValues("prediction.orchestration.enabled=true")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseInstanceOf(PredictionInputException.class);
            });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "model-id= ",
        "execution-kind=UNKNOWN",
        "trials=0",
        "trials=100001",
        "trials=1.5",
        "seed=-1",
        "seed=2147483648",
        "execution-kind=DETERMINISTIC",
        "configuration-json={\"trials\":10}",
        "configuration-json={\"seed\":42}"
      })
  void rejectsInvalidControlsAtStartup(String setting) {
    configured()
        .withPropertyValues("prediction.orchestration.models[0]." + setting)
        .run(context -> assertThat(context).hasFailed());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "null", "[]", "42", "{", "{} {}", "{\"key\":1,\"key\":2}"})
  void rejectsMalformedConfigurationObjects(String json) {
    configured()
        .withPropertyValues("prediction.orchestration.models[0].configuration-json=" + json)
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void applicationPropertiesSupportEnableEnvironmentOverride() throws Exception {
    ResourcePropertySource applicationProperties =
        new ResourcePropertySource(new ClassPathResource("application.properties"));
    runner
        .withInitializer(
            context -> context.getEnvironment().getPropertySources().addLast(applicationProperties))
        .withBean(PredictionDataProvider.class, UnusedDataProvider::new)
        .withPropertyValues(
            "PREDICTION_ORCHESTRATION_ENABLED=true",
            "prediction.orchestration.models[0].model-id=test-model",
            "prediction.orchestration.models[0].execution-kind=DETERMINISTIC")
        .run(
            context ->
                assertThat(context).hasNotFailed().hasSingleBean(PredictionOrchestrator.class));
  }

  private static final class UnusedDataProvider implements PredictionDataProvider {
    @Override
    public Optional<Game> findGame(String gameId) {
      throw new AssertionError("Startup must not retrieve games.");
    }

    @Override
    public Optional<PredictionSnapshot> findSnapshot(int season) {
      throw new AssertionError("Startup must not resolve snapshots.");
    }

    @Override
    public Optional<Team> findTeam(PredictionSnapshot snapshot, String teamId) {
      throw new AssertionError("Startup must not retrieve teams.");
    }
  }
}
