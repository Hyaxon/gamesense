// GameResponse objects shaped like the Games API, for frontend tests.

export const HOME = {
  id: 'home',
  name: 'Oklahoma Sooners',
  shortName: 'OU',
  conference: 'Southeastern Conference',
}
export const AWAY = {
  id: 'away',
  name: 'Texas Longhorns',
  shortName: 'UT',
  conference: 'Southeastern Conference',
}

export function modelResult(
  modelId,
  displayName,
  method,
  winnerTeamId,
  confidence,
) {
  return {
    modelId,
    displayName,
    prediction: {
      id: `${modelId}-prediction`,
      method,
      predictedWinnerTeamId: winnerTeamId,
      confidence,
      generatedAt: '2026-09-01T12:00:00Z',
    },
  }
}

export const AVAILABLE = {
  state: 'AVAILABLE',
  models: [
    modelResult('elo-v1', 'Elo', 'ELO', 'home', 0.71),
    modelResult('glicko2', 'Glicko-2', 'GLICKO2', 'away', 0.55),
  ],
}

export const AGGREGATE = {
  id: 'aggregate',
  aggregationStrategy: 'AVERAGE',
  contributingResultIds: ['elo-v1-prediction', 'glicko2-prediction'],
  predictedWinnerTeamId: 'home',
  confidence: 0.67,
  generatedAt: '2026-09-01T12:00:00Z',
}

export function gameResponse(overrides = {}) {
  return {
    id: 'game',
    season: 2026,
    homeTeamId: 'home',
    awayTeamId: 'away',
    scheduledAt: '2026-09-12T00:00:00Z',
    status: 'SCHEDULED',
    isNeutralSite: false,
    homeTeam: HOME,
    awayTeam: AWAY,
    ...overrides,
  }
}
