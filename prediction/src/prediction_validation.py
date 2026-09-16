"""Validation helpers for standardized prediction data.

Provides reusable checks to ensure prediction results contain the required
fields and valid values before they are used by other application components.
"""
def is_valid_prediction(prediction: dict) -> bool:
    # Use camelCase for dictionary keys because predictions are shared logical objects
    # exchanged across multiple languages and should follow a consistent data schema.
    method = prediction.get("method")
    predicted_winner = prediction.get("predictedWinner")
    confidence_score = prediction.get("confidenceScore")

    # strip() ensures that all whitespace-only strings are rejected.
    if not isinstance(method, str) or not method.strip():
        return False

    if not isinstance(predicted_winner, str) or not predicted_winner.strip():
        return False

    # In Python, bool is a subclass of int, so boolean values must be rejected separately.
    if isinstance(confidence_score, bool) or not isinstance(
        confidence_score, (int, float)
    ):
        return False

    return 0.0 <= confidence_score <= 1.0
