"""Compatibility imports for the standalone Glicko demonstration."""

from gamesense_prediction.models.glicko.glicko2 import (
    Team,
    calculate_delta,
    calculate_variance,
    expected_score,
    g,
    inactive_rd_update,
    mu_to_rating,
    phi_to_rd,
    probability_prediction,
    rating_to_mu,
    rd_to_phi,
    update_rating,
)

__all__ = [
    "Team",
    "calculate_delta",
    "calculate_variance",
    "expected_score",
    "g",
    "inactive_rd_update",
    "mu_to_rating",
    "phi_to_rd",
    "prediction",
    "probability_prediction",
    "rating_to_mu",
    "rd_to_phi",
    "update_rating",
]


def prediction(team_a, team_b):

    # Convert ratings
    mu_a = rating_to_mu(team_a.rating)
    mu_b = rating_to_mu(team_b.rating)

    phi_b = rd_to_phi(team_b.rd)

    # Win probability
    probability_a = expected_score(mu_a, mu_b, phi_b)

    probability_b = 1 - probability_a

    # Rating uncertainty
    rd_confidence = 1 - ((team_a.rd + team_b.rd) / (2 * 350))

    # How decisive is the prediction?
    decisiveness = abs(probability_a - 0.5) * 2

    # Combine the two
    confidence = rd_confidence * decisiveness

    return probability_a, probability_b, confidence
