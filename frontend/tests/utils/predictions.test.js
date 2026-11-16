import { describe, expect, it } from 'vitest'
import { mapGameToCard, mapPastGameToCard } from '../../src/utils/mapGameToCard'
import {
  consensusOutcome,
  pollWhilePending,
  summarizeConsensus,
  summarizeModels,
} from '../../src/utils/predictions'
import {
  AGGREGATE,
  AVAILABLE,
  gameResponse,
  modelResult,
} from '../fixtures/games'

describe('mapPredictions', () => {
  it('marks games the API never predicted', () => {
    expect(mapGameToCard(gameResponse()).predictions).toEqual({
      state: 'NONE',
      models: [],
      consensus: null,
    })
  })

  it('maps every returned model in API order with its picked team', () => {
    const { state, models, consensus } = mapGameToCard(
      gameResponse({ predictions: AVAILABLE }),
    ).predictions

    expect(state).toBe('AVAILABLE')
    expect(consensus).toBeNull()
    expect(models).toHaveLength(2)
    expect(models[0]).toMatchObject({
      modelId: 'elo-v1',
      name: 'Elo',
      description: 'Ratings-based model',
      side: 'home',
      abbreviation: 'OU',
      teamName: 'Oklahoma Sooners',
      percentage: 71,
    })
    expect(models[1]).toMatchObject({
      name: 'Glicko-2',
      side: 'away',
      abbreviation: 'UT',
      percentage: 55,
    })
  })

  it('maps an aggregate to the consensus pick', () => {
    const { consensus } = mapGameToCard(
      gameResponse({ predictions: { ...AVAILABLE, aggregate: AGGREGATE } }),
    ).predictions

    expect(consensus).toMatchObject({
      side: 'home',
      teamName: 'Oklahoma Sooners',
      percentage: 67,
    })
  })

  it.each(['PENDING', 'UNAVAILABLE'])(
    'keeps the %s state with no models',
    (state) => {
      const { predictions } = mapGameToCard(
        gameResponse({ predictions: { state, models: [] } }),
      )
      expect(predictions.state).toBe(state)
      expect(predictions.models).toEqual([])
    },
  )
})

describe('Game History summaries', () => {
  const final = (overrides) =>
    mapPastGameToCard(
      gameResponse({
        status: 'FINAL',
        homeScore: 21,
        awayScore: 7,
        ...overrides,
      }),
    )

  it('scores consensus picks only for games with an aggregate and a winner', () => {
    const games = [
      final({
        winnerTeamId: 'home',
        predictions: { ...AVAILABLE, aggregate: AGGREGATE },
      }),
      final({
        winnerTeamId: 'away',
        predictions: { ...AVAILABLE, aggregate: AGGREGATE },
      }),
      final({ winnerTeamId: 'home', predictions: AVAILABLE }),
      final({ winnerTeamId: 'home' }),
    ]

    expect(games.map(consensusOutcome)).toEqual([
      'CORRECT',
      'MISSED',
      null,
      null,
    ])
    expect(summarizeConsensus(games)).toEqual({
      reviewed: 4,
      scored: 2,
      correct: 1,
      accuracy: 0.5,
    })
  })

  it('reports no consensus figures before any aggregate exists', () => {
    const summary = summarizeConsensus([
      final({ winnerTeamId: 'home', predictions: AVAILABLE }),
    ])
    expect(summary.scored).toBe(0)
    expect(summary.accuracy).toBeNull()
  })

  it('counts each model only on completed games it predicted', () => {
    const games = [
      final({ winnerTeamId: 'home', predictions: AVAILABLE }),
      final({
        winnerTeamId: 'away',
        predictions: {
          state: 'AVAILABLE',
          models: [modelResult('elo-v1', 'Elo', 'ELO', 'away', 0.6)],
        },
      }),
      // Ties and games without predictions are not scored.
      final({ winnerTeamId: null, homeScore: 7, predictions: AVAILABLE }),
      final({ winnerTeamId: 'home' }),
    ]

    expect(summarizeModels(games)).toEqual([
      { modelId: 'elo-v1', name: 'Elo', correct: 2, total: 2 },
      { modelId: 'glicko2', name: 'Glicko-2', correct: 0, total: 1 },
    ])
  })
})

describe('pollWhilePending', () => {
  const query = (data) => ({ state: { data } })

  it('polls while any game is pending', () => {
    expect(
      pollWhilePending(
        query([
          gameResponse({ predictions: AVAILABLE }),
          gameResponse({ predictions: { state: 'PENDING', models: [] } }),
        ]),
      ),
    ).toBe(5000)
  })

  it('stops once no game is pending', () => {
    expect(
      pollWhilePending(query([gameResponse({ predictions: AVAILABLE })])),
    ).toBe(false)
    expect(pollWhilePending(query(undefined))).toBe(false)
  })
})
