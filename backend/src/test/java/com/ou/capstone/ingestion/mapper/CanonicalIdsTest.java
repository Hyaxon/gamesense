package com.ou.capstone.ingestion.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

// Verifies canonical IDs are stable, distinct per record, and valid UUIDs.
class CanonicalIdsTest {

  @Test
  void sameInputAlwaysGivesSameId() {
    assertThat(CanonicalIds.teamId("Oklahoma Sooners"))
        .isEqualTo(CanonicalIds.teamId("Oklahoma Sooners"));
    assertThat(CanonicalIds.gameId("2025-10-25|A|B"))
        .isEqualTo(CanonicalIds.gameId("2025-10-25|A|B"));
  }

  @Test
  void differentInputsGiveDifferentIds() {
    assertThat(CanonicalIds.teamId("Oklahoma Sooners"))
        .isNotEqualTo(CanonicalIds.teamId("Texas Longhorns"));
  }

  @Test
  void teamAndGameIdsDoNotCollideForTheSameText() {
    assertThat(CanonicalIds.teamId("x")).isNotEqualTo(CanonicalIds.gameId("x"));
  }

  @Test
  void idsAreValidUuids() {
    assertThat(UUID.fromString(CanonicalIds.teamId("Oklahoma Sooners"))).isNotNull();
  }
}
