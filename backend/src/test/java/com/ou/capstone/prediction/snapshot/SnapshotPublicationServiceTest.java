package com.ou.capstone.prediction.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ou.capstone.data.InMemoryCanonicalDataStore;
import com.ou.capstone.prediction.client.PythonPredictionClient;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Publication only changes the selected snapshot after Python acknowledges it. */
class SnapshotPublicationServiceTest {
  private final PredictionSnapshotBuilder builder = mock(PredictionSnapshotBuilder.class);
  private final PythonPredictionClient client = mock(PythonPredictionClient.class);
  private final InMemoryCanonicalDataStore store = new InMemoryCanonicalDataStore();
  private final SnapshotPublicationService service =
      new SnapshotPublicationService(builder, client, store, true);

  @Test
  void storesAcknowledgedId() throws Exception {
    var request = new SnapshotPublishRequest(2026, Map.of("source", "test"), List.of(), List.of());
    when(builder.build(2026)).thenReturn(request);
    when(client.publishSnapshot(request)).thenReturn("python-id");
    service.publishSeason(2026);
    assertThat(store.findSnapshot(2026).orElseThrow().dataSnapshotId()).isEqualTo("python-id");
    assertThat(store.findSnapshot(2025)).isEmpty();
  }

  @Test
  void failedPublishDoesNotInventOrReplaceId() throws Exception {
    var request = new SnapshotPublishRequest(2026, Map.of("source", "test"), List.of(), List.of());
    when(builder.build(2026)).thenReturn(request);
    when(client.publishSnapshot(request)).thenThrow(new IOException("Unavailable"));
    assertThatThrownBy(() -> service.publishSeason(2026)).isInstanceOf(IOException.class);
    assertThat(store.findSnapshot(2026)).isEmpty();
    store.recordPublishedSnapshot(2026, "previous-id");
    assertThatThrownBy(() -> service.publishSeason(2026)).isInstanceOf(IOException.class);
    assertThat(store.findSnapshot(2026).orElseThrow().dataSnapshotId()).isEqualTo("previous-id");
  }

  @Test
  void builderFailureDoesNotCallPython() {
    when(builder.build(2026)).thenThrow(new IllegalArgumentException("Malformed game"));
    assertThatThrownBy(() -> service.publishSeason(2026))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(client);
    assertThat(store.findSnapshot(2026)).isEmpty();
  }

  @Test
  void disabledPublicationDoesNotBuildOrSend() throws Exception {
    new SnapshotPublicationService(builder, client, store, false).publishSeason(2026);
    verifyNoInteractions(builder, client);
    assertThat(store.findSnapshot(2026)).isEmpty();
  }
}
