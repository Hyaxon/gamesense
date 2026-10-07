"""History features, offline training, and XGBoost matchup probabilities."""

from collections.abc import Sequence
from datetime import datetime
from itertools import groupby

import numpy as np
from xgboost import XGBClassifier

from ...context import CompletedGame, MissingContextError, PredictionContext

# The same ordering is used for training and prediction. Team identities are
# lookup keys, not numeric features that imply an ordering between teams.
FEATURE_NAMES = (
    "home_points_scored",
    "home_points_allowed",
    "home_win_rate",
    "away_points_scored",
    "away_points_allowed",
    "away_win_rate",
    "home_advantage",
)


def team_features(team_id: str, games: Sequence[CompletedGame]) -> tuple[float, ...]:
    """Average points scored/allowed and win rate; ties count as half a win."""
    if not games:
        raise MissingContextError("Required completed-game history is unavailable")
    scored = sum(
        game.home_score if game.home_team_id == team_id else game.away_score
        for game in games
    )
    allowed = sum(
        game.away_score if game.home_team_id == team_id else game.home_score
        for game in games
    )
    wins = sum(
        0.5 if game.winner_team_id is None else float(game.winner_team_id == team_id)
        for game in games
    )
    return scored / len(games), allowed / len(games), wins / len(games)


def matchup_features(
    home_team_id: str,
    away_team_id: str,
    home_games: Sequence[CompletedGame],
    away_games: Sequence[CompletedGame],
    is_neutral_site: bool,
) -> tuple[float, ...]:
    return (
        *team_features(home_team_id, home_games),
        *team_features(away_team_id, away_games),
        float(not is_neutral_site),
    )


def build_training_data(context: PredictionContext) -> tuple[np.ndarray, np.ndarray]:
    """Build pregame features using only strictly earlier completed games.

    Skip labels for ties and games where either team has no prior history.
    These games still contribute to later history. Games sharing an instant
    cannot use one another's results, regardless of the snapshot's ID ordering.
    """
    history: dict[str, list[CompletedGame]] = {team_id: [] for team_id in context.teams}
    rows = []
    labels = []
    for _, group in groupby(
        context.completed_games,
        key=lambda game: datetime.fromisoformat(game.scheduled_at),
    ):
        games = tuple(group)
        for game in games:
            home_games = history[game.home_team_id]
            away_games = history[game.away_team_id]
            if game.winner_team_id is None or not home_games or not away_games:
                continue
            rows.append(
                matchup_features(
                    game.home_team_id,
                    game.away_team_id,
                    home_games,
                    away_games,
                    game.is_neutral_site,
                )
            )
            labels.append(int(game.winner_team_id == game.home_team_id))
        for game in games:
            history[game.home_team_id].append(game)
            history[game.away_team_id].append(game)

    return (
        np.asarray(rows, dtype=np.float32).reshape(-1, len(FEATURE_NAMES)),
        np.asarray(labels, dtype=np.int32),
    )


def train_model(context: PredictionContext) -> XGBClassifier:
    """Fit once offline, never inside the adapter's execute method."""
    features, labels = build_training_data(context)
    if set(labels.tolist()) != {0, 1}:
        raise ValueError(
            "Training requires both home and away wins with prior history for both teams"
        )
    model = XGBClassifier(
        objective="binary:logistic",
        eval_metric="logloss",
        n_estimators=50,
        max_depth=2,
        learning_rate=0.1,
        tree_method="hist",
        random_state=42,
        n_jobs=1,
    )
    model.fit(features, labels)
    return model


def predict_matchup(
    model: XGBClassifier,
    home_team_id: str,
    away_team_id: str,
    context: PredictionContext,
    is_neutral_site: bool,
) -> float:
    """Return P(home wins) from a previously trained binary classifier.

    The supplied snapshot is the history available for this prediction. Callers
    must select an earlier snapshot when backtesting a historical matchup.
    """
    row = matchup_features(
        home_team_id,
        away_team_id,
        context.require_history(home_team_id),
        context.require_history(away_team_id),
        is_neutral_site,
    )
    return float(model.predict_proba(np.asarray([row], dtype=np.float32))[0, 1])
