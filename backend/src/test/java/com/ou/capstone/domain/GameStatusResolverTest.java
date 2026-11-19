package com.ou.capstone.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// Verifies a game's status is worked out correctly from its stored status and the clock.
class GameStatusResolverTest {

  // A game dated Saturday 14 November: the feed gives only a date, so this is noon UTC.
  private static final Instant START = Instant.parse("2026-11-14T12:00:00Z");

  private static Game game(GameStatus status, Instant scheduledAt) {
    return new Game(
        "g", "g", 2026, null, "home", "away", null, false, scheduledAt, status, null, null, null);
  }

  private static GameStatus statusAt(Game game, Instant now) {
    return GameStatusResolver.resolve(game, now);
  }

  @Test
  void gameThatHasNotStartedIsScheduled() {
    Game game = game(GameStatus.SCHEDULED, START);

    assertThat(statusAt(game, START.minusSeconds(1))).isEqualTo(GameStatus.SCHEDULED);
    assertThat(statusAt(game, START.minus(java.time.Duration.ofDays(30))))
        .isEqualTo(GameStatus.SCHEDULED);
  }

  @Test
  void gameIsInProgressFromItsStartUntilTheWindowEnds() {
    Game game = game(GameStatus.SCHEDULED, START);
    Instant windowEnd = START.plus(GameStatusResolver.IN_PROGRESS_WINDOW);

    assertThat(statusAt(game, START)).isEqualTo(GameStatus.IN_PROGRESS);
    assertThat(statusAt(game, START.plusSeconds(3600))).isEqualTo(GameStatus.IN_PROGRESS);
    assertThat(statusAt(game, windowEnd.minusSeconds(1))).isEqualTo(GameStatus.IN_PROGRESS);
  }

  @Test
  void gameWithNoResultAfterTheWindowIsPostponed() {
    Game game = game(GameStatus.SCHEDULED, START);
    Instant windowEnd = START.plus(GameStatusResolver.IN_PROGRESS_WINDOW);

    assertThat(statusAt(game, windowEnd)).isEqualTo(GameStatus.POSTPONED);
    assertThat(statusAt(game, windowEnd.plusSeconds(86_400 * 30L))).isEqualTo(GameStatus.POSTPONED);
  }

  @Test
  void aGameIsNotInProgressTheEveningBefore() {
    Instant fridayEvening = OffsetDateTime.parse("2026-11-13T20:00:00-06:00").toInstant();
    Game friday = game(GameStatus.SCHEDULED, Instant.parse("2026-11-13T12:00:00Z"));

    assertThat(statusAt(game(GameStatus.SCHEDULED, START), fridayEvening))
        .isEqualTo(GameStatus.SCHEDULED);
    assertThat(statusAt(friday, fridayEvening)).isEqualTo(GameStatus.IN_PROGRESS);
  }

  @Test
  void aLateGameIsStillInProgressWhenItEnds() {
    // A 10:30pm Hawaii kickoff on Saturday is 08:30 UTC on Sunday, and it ends after that.
    Instant lateHawaii = OffsetDateTime.parse("2026-11-14T23:30:00-10:00").toInstant();

    assertThat(statusAt(game(GameStatus.SCHEDULED, START), lateHawaii))
        .isEqualTo(GameStatus.IN_PROGRESS);
  }

  @Test
  void aUsEveningGameIsInProgressAtKickoff() {
    Instant kickoff = OffsetDateTime.parse("2026-11-14T22:00:00-05:00").toInstant();

    assertThat(statusAt(game(GameStatus.SCHEDULED, START), kickoff))
        .isEqualTo(GameStatus.IN_PROGRESS);
  }

  @Test
  void theSameMomentGivesTheSameStatusInAnyTimeZone() {
    Game game = game(GameStatus.SCHEDULED, START);
    // All three are the exact same instant as START, written in different time zones.
    for (String sameMoment :
        new String[] {
          "2026-11-14T12:00:00Z", "2026-11-14T07:00:00-05:00", "2026-11-14T21:00:00+09:00"
        }) {
      assertThat(statusAt(game, OffsetDateTime.parse(sameMoment).toInstant()))
          .isEqualTo(GameStatus.IN_PROGRESS);
    }
    // One second earlier is still scheduled, whichever zone it is written in.
    assertThat(statusAt(game, OffsetDateTime.parse("2026-11-14T06:59:59-05:00").toInstant()))
        .isEqualTo(GameStatus.SCHEDULED);
    assertThat(statusAt(game, OffsetDateTime.parse("2026-11-14T20:59:59+09:00").toInstant()))
        .isEqualTo(GameStatus.SCHEDULED);
  }

  @ParameterizedTest
  @EnumSource(
      value = GameStatus.class,
      names = {"FINAL", "POSTPONED", "CANCELLED", "IN_PROGRESS"})
  void aStatusTheSourceStatesIsKeptWhateverTheClockSays(GameStatus stated) {
    Game game = game(stated, START);

    assertThat(statusAt(game, START.minusSeconds(86_400))).isEqualTo(stated);
    assertThat(statusAt(game, START.plusSeconds(3600))).isEqualTo(stated);
    assertThat(statusAt(game, START.plusSeconds(86_400 * 30L))).isEqualTo(stated);
  }

  @Test
  void scheduledGameWithNoTimeStaysScheduled() {
    assertThat(statusAt(game(GameStatus.SCHEDULED, null), START)).isEqualTo(GameStatus.SCHEDULED);
  }
}
