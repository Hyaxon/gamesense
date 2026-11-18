# Xander Meadows
# Glicko-2 rating system functions

import math


class Team:
    def __init__(self, name=None):
        self.name = name
        self.rating = 1500  # Initial Glicko-2 rating
        self.rd = 350  # Initial rating deviation
        self.volatility = 0.06  # Initial volatility


def g(phi):
    # Calculate g(phi) (Reduce effect of uncertain opponents)
    return 1 / math.sqrt(1 + 3 * phi**2 / (math.pi**2))


def expected_score(mu, mu_opponent, phi_opponent):
    # Calculate E (Expected result)
    logit = g(phi_opponent) * (mu - mu_opponent)
    if logit >= 0:
        probability = 1 / (1 + math.exp(-logit))
    else:
        exp_logit = math.exp(logit)
        probability = exp_logit / (1 + exp_logit)

    # Keep variance calculations finite when ratings are far apart.
    return min(max(probability, 1e-12), 1 - 1e-12)


def calculate_variance(mu, mu_opponents, phi_opponents):
    # Calculate v (Variance of the rating / info from games)
    v_inv = 0
    for mu_opponent, phi_opponent in zip(mu_opponents, phi_opponents, strict=False):
        expectation = expected_score(mu, mu_opponent, phi_opponent)
        g_phi = g(phi_opponent)
        v_inv += (g_phi**2) * expectation * (1 - expectation)
    return 1 / v_inv


def calculate_delta(mu, mu_opponents, phi_opponents, scores):
    # Calculate Delta (Estimated improvement / difference between expected and actual scores)
    v = calculate_variance(mu, mu_opponents, phi_opponents)
    delta = 0
    for mu_opponent, phi_opponent, score in zip(
        mu_opponents, phi_opponents, scores, strict=False
    ):
        expectation = expected_score(mu, mu_opponent, phi_opponent)
        g_phi = g(phi_opponent)
        delta += g_phi * (score - expectation)
    return v * delta


def update_rating(mu, phi, mu_opponents, phi_opponents, scores):
    # Perform the whole Glicko-2 update
    v = calculate_variance(mu, mu_opponents, phi_opponents)
    delta = calculate_delta(mu, mu_opponents, phi_opponents, scores)
    # Update the rating (mu) and rating deviation (phi)
    phi_new = (1 / (1 / phi**2 + 1 / v)) ** 0.5
    # delta is v * sum(g * (score - expected)). It must be scaled by
    # phi_new^2 / v when converting the estimated improvement to mu.
    mu_new = mu + (phi_new**2 / v) * delta
    return mu_new, phi_new


def update_rating_period(
    mu,
    phi,
    volatility,
    mu_opponents,
    phi_opponents,
    scores,
    tau=0.5,
):
    """Apply one complete Glicko-2 rating-period update.

    Unlike ``update_rating``, this also updates volatility using the iterative
    step from the Glicko-2 specification.  Inputs and outputs use the
    internal ``mu``, ``phi`` and volatility units.
    """
    if not mu_opponents:
        return mu, math.sqrt(phi**2 + volatility**2), volatility

    v = calculate_variance(mu, mu_opponents, phi_opponents)
    delta = calculate_delta(mu, mu_opponents, phi_opponents, scores)
    a = math.log(volatility**2)

    def volatility_objective(x):
        exp_x = math.exp(x)
        denominator = phi**2 + v + exp_x
        return (
            exp_x * (delta**2 - phi**2 - v - exp_x) / (2 * denominator**2)
            - (x - a) / tau**2
        )

    if delta**2 > phi**2 + v:
        bracket_b = math.log(delta**2 - phi**2 - v)
    else:
        k = 1
        while volatility_objective(a - k * tau) < 0:
            k += 1
        bracket_b = a - k * tau

    objective_b = volatility_objective(bracket_b)
    bracket_a = a
    for _ in range(100):
        candidate = (bracket_a + bracket_b) / 2
        objective_candidate = volatility_objective(candidate)
        if abs(objective_candidate) < 1e-12:
            break
        if objective_candidate * objective_b < 0:
            bracket_a = bracket_b
        bracket_b, objective_b = candidate, objective_candidate

    updated_volatility = math.exp(bracket_b / 2)
    phi_star = math.sqrt(phi**2 + updated_volatility**2)
    updated_phi = 1 / math.sqrt(1 / phi_star**2 + 1 / v)
    updated_mu = mu + updated_phi**2 * delta / v
    return updated_mu, updated_phi, updated_volatility


def rating_to_mu(rating):
    return (rating - 1500) / 173.7178


def rd_to_phi(rd):
    return rd / 173.7178


def mu_to_rating(mu):
    return 173.7178 * mu + 1500


def phi_to_rd(phi):
    return 173.7178 * phi


def inactive_rd_update(phi, v):
    # Update the rating deviation (phi) after inactivity
    return (phi**2 + v) ** 0.5


def probability_prediction(team_a, team_b):
    # Use all the data from two teams to predict the outcome of a game. Return percentage of Team A winning over Team B.
    mu_a = rating_to_mu(team_a.rating)
    mu_b = rating_to_mu(team_b.rating)
    phi_b = rd_to_phi(team_b.rd)
    return expected_score(mu_a, mu_b, phi_b)
