# Xander Meadows
# Glicko-2 rating system functions

import math

def g(phi):
    # Calculate g(phi) (Reduce effect of uncertain opponents)
    return 1 / math.sqrt(1 + 3 * phi ** 2 / (math.pi ** 2))

def expected_score(mu, mu_opponent, phi_opponent):
    # Calculate E (Expected result)
    return 1 / (1 + math.exp(-g(phi_opponent) * (mu - mu_opponent)))

def calculate_variance(mu, mu_opponents, phi_opponents):
    # Calculate v (Variance of the rating / info from games)
    v_inv = 0
    for mu_opponent, phi_opponent in zip(mu_opponents, phi_opponents):
        E = expected_score(mu, mu_opponent, phi_opponent)
        g_phi = g(phi_opponent)
        v_inv += (g_phi ** 2) * E * (1 - E)
    return 1 / v_inv

def calculate_delta(mu, mu_opponents, phi_opponents, scores):
    # Calculate Delta (Estimated improvement / difference between expected and actual scores)
    v = calculate_variance(mu, mu_opponents, phi_opponents)
    delta = 0
    for mu_opponent, phi_opponent, score in zip(mu_opponents, phi_opponents, scores):
        E = expected_score(mu, mu_opponent, phi_opponent)
        g_phi = g(phi_opponent)
        delta += g_phi * (score - E)
    return v * delta

def update_rating(mu, phi, mu_opponents, phi_opponents, scores):
    # Perform the whole Glicko-2 update
    v = calculate_variance(mu, mu_opponents, phi_opponents)
    delta = calculate_delta(mu, mu_opponents, phi_opponents, scores)
    # Update the rating (mu) and rating deviation (phi)
    mu_new = mu + delta
    phi_new = (1 / (1 / phi ** 2 + 1 / v)) ** 0.5
    return mu_new, phi_new

def rating_to_mu(rating):
    return (rating - 1500) / 173.7178

def rd_to_phi(rd):
    return rd / 173.7178

def mu_to_rating(mu):
    return 173.7178 * mu + 1500

def phi_to_rd(phi):
    return 173.7178 * phi