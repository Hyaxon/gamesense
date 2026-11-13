package com.ou.capstone.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Works out a game's status at a given moment, from its stored status and the clock.
 *
 * <p>A status the source states outright (final, cancelled, postponed, in progress) is kept. A game
 * still marked scheduled is judged by time alone: it is scheduled until its start, in progress for
 * a window after that, and postponed if no result has arrived by then. All comparisons use
 * instants, so time zones cannot change the answer.
 */
public final class GameStatusResolver {

  // The feed has dates but no kickoff times, so scheduledAt is midnight UTC of the game day. A US
  // evening game is played several hours after that, so a game counts as in progress for 36 hours.
  static final Duration IN_PROGRESS_WINDOW = Duration.ofHours(36);

  private GameStatusResolver() {}

  // Returns the status the game has at the given moment.
  public static GameStatus resolve(Game game, Instant now) {
    if (game.status() != GameStatus.SCHEDULED || game.scheduledAt() == null) {
      return game.status();
    }
    if (now.isBefore(game.scheduledAt())) {
      return GameStatus.SCHEDULED;
    }
    if (now.isBefore(game.scheduledAt().plus(IN_PROGRESS_WINDOW))) {
      return GameStatus.IN_PROGRESS;
    }
    // The game should be over but no result was reported. A later load with a final score
    // replaces this.
    return GameStatus.POSTPONED;
  }
}
