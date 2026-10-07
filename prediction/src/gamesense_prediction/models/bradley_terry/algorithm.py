"""Bradley-Terry strength fitting and matchup probabilities.

Independent of the wire contract and runner. Strengths are fitted from every
completed game in the supplied snapshot with the MM (Zermelo) iteration:

    p_i <- (W_i + prior) / (sum_j n_ij / (p_i + p_j) + 2 * prior / (p_i + 1))

Plain maximum likelihood fails on real data: a team with no wins (or no
losses) has strength 0 (or infinity), and strengths are only defined up to a
scale. The prior fixes both: every team gets `prior_games` virtual wins and
losses against a phantom opponent of strength 1.0. All strengths stay finite
and positive, and 1.0 means "average team".
"""

from collections import defaultdict
from collections.abc import Sequence

from ...context import PredictionContext

DEFAULT_PRIOR_GAMES = 0.5
DEFAULT_MAX_ITERATIONS = 1000
DEFAULT_TOLERANCE = 1e-9


def fit_strengths(
    games: Sequence,
    prior_games: float = DEFAULT_PRIOR_GAMES,
    max_iterations: int = DEFAULT_MAX_ITERATIONS,
    tolerance: float = DEFAULT_TOLERANCE,
) -> dict[str, float]:
    """Fit one strength per team. A tie (winner_team_id None) is half a win each."""
    played: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
    wins: dict[str, float] = defaultdict(float)

    for game in games:
        home, away = game.home_team_id, game.away_team_id
        if home == away:
            continue
        played[home][away] += 1
        played[away][home] += 1
        if game.winner_team_id is None:
            wins[home] += 0.5
            wins[away] += 0.5
        else:
            wins[game.winner_team_id] += 1.0

    strengths = {team_id: 1.0 for team_id in played}
    for _ in range(max_iterations):
        updated = {}
        largest_change = 0.0
        for team_id, opponents in played.items():
            current = strengths[team_id]
            denominator = sum(
                count / (current + strengths[opponent])
                for opponent, count in opponents.items()
            )
            denominator += 2 * prior_games / (current + 1.0)
            new_value = (wins[team_id] + prior_games) / denominator
            largest_change = max(largest_change, abs(new_value - current) / current)
            updated[team_id] = new_value
        strengths = updated
        if largest_change < tolerance:
            break
    return strengths


def win_probability(strength_a: float, strength_b: float) -> float:
    """P(A beats B) under the Bradley-Terry model."""
    return strength_a / (strength_a + strength_b)


def predict_matchup(
    home_team_id: str, away_team_id: str, context: PredictionContext
) -> float:
    """Return P(home wins) using strengths fitted on the supplied snapshot.

    Uses require_history so a team with no completed games becomes NOT_FOUND
    rather than an invented average. The snapshot is the history available for
    this prediction; callers backtesting must supply an earlier snapshot.
    """
    context.require_history(home_team_id)
    context.require_history(away_team_id)
    strengths = fit_strengths(context.completed_games)
    return win_probability(strengths[home_team_id], strengths[away_team_id])
