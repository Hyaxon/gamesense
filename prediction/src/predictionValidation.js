export function isValidPrediction(prediction) {
  const method = prediction.method;
  const predictedWinner = prediction.predictedWinner;
  const confidenceScore = prediction.confidenceScore;

  // Reject missing, non-string, and whitespace-only values.
  if (typeof method !== "string" || !method.trim()) {
    return false;
  }

  if (typeof predictedWinner !== "string" || !predictedWinner.trim()) {
    return false;
  }

  if (typeof confidenceScore !== "number" || !Number.isFinite(confidenceScore)) {
    return false;
  }

  return confidenceScore >= 0.0 && confidenceScore <= 1.0;
}
