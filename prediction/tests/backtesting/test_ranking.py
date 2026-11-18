import random

from gamesense_prediction.backtesting.ranking import Criterion, rank_models
from gamesense_prediction.backtesting.results import ModelSummary


def summary(model_id, *, correct, incorrect, brier, log_loss=0.6):
    decided = correct + incorrect
    return ModelSummary(
        model_id=model_id,
        games=decided,
        predicted=decided,
        correct=correct,
        incorrect=incorrect,
        ties=0,
        no_prediction=0,
        metrics={
            "accuracy": correct / decided if decided else None,
            "brier_score": brier,
            "log_loss": log_loss,
            "coverage": 1.0 if decided else None,
        },
    )


def order(ranking):
    return [(entry.summary.model_id, entry.rank) for entry in ranking]


def test_higher_accuracy_ranks_first():
    ranking = rank_models(
        [
            summary("coin", correct=20, incorrect=20, brier=0.25),  # 0.50
            summary("elo", correct=30, incorrect=10, brier=0.30),  # 0.75
            summary("glicko", correct=24, incorrect=16, brier=0.10),  # 0.60
        ]
    )

    assert order(ranking) == [("elo", 1), ("glicko", 2), ("coin", 3)]


def test_lower_brier_score_breaks_an_accuracy_tie():
    ranking = rank_models(
        [
            summary("elo", correct=28, incorrect=12, brier=0.20),
            summary("glicko", correct=28, incorrect=12, brier=0.18),
        ]
    )

    assert order(ranking) == [("glicko", 1), ("elo", 2)]


def test_a_missing_brier_score_loses_the_tie_break():
    ranking = rank_models(
        [
            summary("no-probabilities", correct=28, incorrect=12, brier=None),
            summary("elo", correct=28, incorrect=12, brier=0.30),
        ]
    )

    assert order(ranking) == [("elo", 1), ("no-probabilities", 2)]


def test_models_equal_on_accuracy_and_brier_share_a_rank():
    ranking = rank_models(
        [
            summary("xgboost", correct=42, incorrect=18, brier=0.21),  # 0.70
            summary("bradley-terry", correct=21, incorrect=9, brier=0.21),  # 0.70
            summary("coin", correct=20, incorrect=20, brier=0.25),
            summary("elo", correct=32, incorrect=8, brier=0.15),  # 0.80
        ]
    )

    # Model ID only orders the tied pair. The rank after a shared rank skips ahead.
    assert order(ranking) == [
        ("elo", 1),
        ("bradley-terry", 2),
        ("xgboost", 2),
        ("coin", 4),
    ]


def test_models_below_thirty_decided_games_are_unranked_and_listed_last():
    ranking = rank_models(
        [
            summary("perfect-but-small", correct=29, incorrect=0, brier=0.01),
            summary("no-games", correct=0, incorrect=0, brier=None),
            summary("exactly-thirty", correct=15, incorrect=15, brier=0.25),
            summary("elo", correct=30, incorrect=10, brier=0.20),
        ]
    )

    assert order(ranking) == [
        ("elo", 1),
        ("exactly-thirty", 2),
        ("no-games", None),
        ("perfect-but-small", None),
    ]


def test_the_cutoff_and_criteria_can_be_changed():
    small = summary("small", correct=9, incorrect=1, brier=0.30)
    large = summary("large", correct=24, incorrect=16, brier=0.10)

    assert order(rank_models([small, large])) == [("large", 1), ("small", None)]
    assert order(rank_models([small, large], min_decided_games=10)) == [
        ("small", 1),
        ("large", 2),
    ]
    brier_only = [Criterion("brier_score", higher_is_better=False)]
    assert order(
        rank_models([small, large], criteria=brier_only, min_decided_games=10)
    ) == [("large", 1), ("small", 2)]


def test_every_model_keeps_its_metric_values():
    elo = summary("elo", correct=30, incorrect=10, brier=0.20, log_loss=0.55)
    small = summary("small", correct=5, incorrect=5, brier=0.25, log_loss=0.69)

    ranking = rank_models([elo, small])

    assert [entry.summary for entry in ranking] == [elo, small]


def test_the_same_summaries_in_any_order_give_the_same_ranking():
    summaries = [
        summary("elo", correct=28, incorrect=12, brier=0.20),
        summary("glicko", correct=28, incorrect=12, brier=0.18),
        summary("xgboost", correct=42, incorrect=18, brier=0.21),
        summary("bradley-terry", correct=21, incorrect=9, brier=0.21),
        summary("coin", correct=20, incorrect=20, brier=0.25),
        summary("small", correct=10, incorrect=0, brier=0.05),
        summary("empty", correct=0, incorrect=0, brier=None),
    ]
    expected = order(rank_models(summaries))

    for seed in range(20):
        shuffled = summaries[:]
        random.Random(seed).shuffle(shuffled)
        assert order(rank_models(shuffled)) == expected
    assert expected == [
        ("glicko", 1),
        ("elo", 2),
        ("bradley-terry", 3),
        ("xgboost", 3),
        ("coin", 5),
        ("empty", None),
        ("small", None),
    ]
