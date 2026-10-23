package com.ou.capstone.domain;

/** Canonical game lifecycle values from the shared GameStatus schema. */
public enum GameStatus {
  SCHEDULED,
  IN_PROGRESS,
  FINAL,
  POSTPONED,
  CANCELLED
}
