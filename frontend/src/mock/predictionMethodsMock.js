// Mock data shaped like PredictionResponse.predictions / .aggregate, until #69 connects the API.
export const MOCK_TEAM_A = { id: 'team-michigan', name: 'Michigan' }
const TEAM_B_ID = 'team-ohio-state'

// Some models favor Team A, others favor Team B (so Team A's chance is below 50%).
export const MOCK_PREDICTIONS = [
  {
    id: 'p1',
    method: 'BRADLEY_TERRY',
    predictedWinnerTeamId: 'team-michigan',
    confidence: 0.62,
  },
  {
    id: 'p2',
    method: 'GLICKO2',
    predictedWinnerTeamId: TEAM_B_ID,
    confidence: 0.55,
  },
  {
    id: 'p3',
    method: 'MACHINE_LEARNING',
    predictedWinnerTeamId: 'team-michigan',
    confidence: 0.58,
  },
  {
    id: 'p4',
    method: 'ELO',
    predictedWinnerTeamId: TEAM_B_ID,
    confidence: 0.51,
  },
  {
    id: 'p5',
    method: 'MONTE_CARLO',
    predictedWinnerTeamId: 'team-michigan',
    confidence: 0.6,
  },
  {
    id: 'p6',
    method: 'TRUESKILL',
    predictedWinnerTeamId: TEAM_B_ID,
    confidence: 0.57,
  },
]

export const MOCK_AGGREGATE = {
  predictedWinnerTeamId: 'team-michigan',
  confidence: 0.54,
}

// Accuracy tiers (from #121) by method. TRUESKILL has none, to show a row without a badge.
export const MOCK_TIERS = {
  CONSENSUS: 'HIGH',
  BRADLEY_TERRY: 'HIGH',
  GLICKO2: 'MEDIUM',
  MACHINE_LEARNING: 'HIGH',
  ELO: 'LOW',
  MONTE_CARLO: 'MEDIUM',
}
