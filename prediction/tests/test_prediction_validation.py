"""Static tests for prediction validation behavior.

Verifies that valid prediction data is accepted and malformed or invalid
prediction data is rejected consistently.
"""
from src.prediction_validation import is_valid_prediction


def test_valid_prediction():
    prediction = {
        "method": "ELO",
        "predictedWinner": "Thunder",
        "confidenceScore": 0.72,
    }

    assert is_valid_prediction(prediction) is True


def test_missing_method_is_invalid():
    prediction = {
        "predictedWinner": "Thunder",
        "confidenceScore": 0.72,
    }

    assert is_valid_prediction(prediction) is False


def test_whitespace_predicted_winner_is_invalid():
    prediction = {
        "method": "Glicko",
        "predictedWinner": "   ",
        "confidenceScore": 0.68,
    }

    assert is_valid_prediction(prediction) is False


def test_confidence_score_out_of_range_is_invalid():
    prediction = {
        "method": "ELO",
        "predictedWinner": "Thunder",
        "confidenceScore": 1.25,
    }

    assert is_valid_prediction(prediction) is False


def test_boolean_confidence_score_is_invalid():
    prediction = {
        "method": "ELO",
        "predictedWinner": "Thunder",
        "confidenceScore": True,
    }

    assert is_valid_prediction(prediction) is False
