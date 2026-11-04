package com.ou.capstone.ingestion.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

  // The prediction service rejects any ID that is not a version 4 UUID in lowercase form.
  @Test
  void idsAreVersion4UuidsInCanonicalForm() {
    for (String id :
        List.of(
            CanonicalIds.teamId("Oklahoma Sooners"),
            CanonicalIds.teamId("Texas A&M Aggies"),
            CanonicalIds.gameId("2026-09-05|Michigan State Spartans|Oklahoma Sooners"))) {
      UUID parsed = UUID.fromString(id);
      assertThat(parsed.version()).isEqualTo(4);
      assertThat(parsed.variant()).isEqualTo(2);
      assertThat(parsed.toString()).isEqualTo(id);
    }
  }

  // IDs must never change between releases, or previously published data would stop matching.
  @Test
  void idsStayStableAcrossReleases() {
    assertThat(CanonicalIds.teamId("Oklahoma Sooners"))
        .isEqualTo("63b3aacb-daa6-4652-8f67-c883fd240209");
    assertThat(CanonicalIds.gameId("2026-09-05|Michigan State Spartans|Oklahoma Sooners"))
        .isEqualTo("03ae582e-987a-493b-b40f-77f9fecc819d");
  }
}
