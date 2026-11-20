import { describe, expect, it } from 'vitest'

import {
  formatTeamAProbability,
  getAccuracyTier,
  getModelName,
  teamAWinProbability,
} from '../../src/utils/predictionMethods.js'

describe('teamAWinProbability', () => {
  it('uses the confidence when Team A is the predicted winner', () => {
    const prediction = { predictedWinnerTeamId: 'a', confidence: 0.62 }

    expect(teamAWinProbability(prediction, 'a')).toBe(0.62)
  })

  it('uses the rest when Team B is the predicted winner', () => {
    const prediction = { predictedWinnerTeamId: 'b', confidence: 0.75 }

    expect(teamAWinProbability(prediction, 'a')).toBeCloseTo(0.25)
  })

  it('gives an even matchup 50% either way', () => {
    const prediction = { predictedWinnerTeamId: 'b', confidence: 0.5 }

    expect(teamAWinProbability(prediction, 'a')).toBe(0.5)
  })
})

describe('formatTeamAProbability', () => {
  it('rounds to a whole percentage and names the team', () => {
    expect(formatTeamAProbability(0.624, 'Michigan')).toBe('62% Michigan')
    expect(formatTeamAProbability(0.625, 'Michigan')).toBe('63% Michigan')
    expect(formatTeamAProbability(0.25, 'Michigan')).toBe('25% Michigan')
  })
})

describe('getAccuracyTier', () => {
  it('returns a distinct badge for each tier', () => {
    expect(getAccuracyTier('HIGH').className).toBe('tier-high')
    expect(getAccuracyTier('MEDIUM').className).toBe('tier-medium')
    expect(getAccuracyTier('LOW').className).toBe('tier-low')
  })

  it('returns nothing for a missing or unknown tier', () => {
    expect(getAccuracyTier(undefined)).toBeUndefined()
    expect(getAccuracyTier('GREAT')).toBeUndefined()
  })
})

describe('getModelName', () => {
  it('maps API method values to display names', () => {
    expect(getModelName('BRADLEY_TERRY')).toBe('Bradley-Terry')
    expect(getModelName('GLICKO2')).toBe('Glicko-2')
  })

  it('falls back to the raw value for an unknown method', () => {
    expect(getModelName('NEW_MODEL')).toBe('NEW_MODEL')
  })
})
