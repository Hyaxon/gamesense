import { describe, expect, it } from "vitest";

import { isValidPrediction } from "../src/predictionValidation.js";

describe("isValidPrediction", () => {
  it("accepts a valid prediction", () => {
    const prediction = {
      method: "ELO",
      predictedWinner: "Thunder",
      confidenceScore: 0.72,
    };

    expect(isValidPrediction(prediction)).toBe(true);
  });

  it("rejects a prediction with a missing method", () => {
    const prediction = {
      predictedWinner: "Thunder",
      confidenceScore: 0.72,
    };

    expect(isValidPrediction(prediction)).toBe(false);
  });

  it("rejects a whitespace-only predicted winner", () => {
    const prediction = {
      method: "Glicko",
      predictedWinner: "   ",
      confidenceScore: 0.68,
    };

    expect(isValidPrediction(prediction)).toBe(false);
  });

  it("rejects a confidence score outside the valid range", () => {
    const prediction = {
      method: "ELO",
      predictedWinner: "Thunder",
      confidenceScore: 1.25,
    };

    expect(isValidPrediction(prediction)).toBe(false);
  });

  it("rejects a boolean confidence score", () => {
    const prediction = {
      method: "ELO",
      predictedWinner: "Thunder",
      confidenceScore: true,
    };

    expect(isValidPrediction(prediction)).toBe(false);
  });
});
