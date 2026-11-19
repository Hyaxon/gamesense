// Display names for the PredictionMethod values the API returns.
const METHOD_NAMES = {
  ELO: 'ELO',
  GLICKO2: 'Glicko-2',
  TRUESKILL: 'TrueSkill',
  MONTE_CARLO: 'Monte Carlo',
  MACHINE_LEARNING: 'Machine Learning',
  BRADLEY_TERRY: 'Bradley-Terry',
  HOME_GROWN: 'Home Grown',
  RANDOM: 'Random',
}

// Accuracy tiers (from #121) mapped to the label and style the badge uses.
const TIERS = {
  HIGH: { label: 'High', className: 'tier-high' },
  MEDIUM: { label: 'Medium', className: 'tier-medium' },
  LOW: { label: 'Low', className: 'tier-low' },
}

// Returns the name to show for a method, e.g. BRADLEY_TERRY -> Bradley-Terry.
export function getModelName(method) {
  return METHOD_NAMES[method] || method || 'Model'
}

// Returns a short description of what a model is based on.
export function getModelDesc(method = '') {
  const lower = method.toLowerCase()
  if (lower.includes('bradley') || lower.includes('elo'))
    return 'Ratings-based model'
  if (lower.includes('glicko')) return 'Dynamic ratings model'
  if (lower.includes('machine')) return 'AI prediction model'
  if (lower.includes('monte')) return 'Random chance baseline'
  return 'Prediction model'
}

// Returns the badge for an accuracy tier, or undefined when the tier is missing or unknown.
export function getAccuracyTier(tier) {
  return TIERS[tier]
}

// Converts a prediction's confidence (the chance its predicted winner wins) into Team A's
// win probability: the confidence if Team A is the predicted winner, otherwise the rest.
export function teamAWinProbability(prediction, teamAId) {
  return prediction.predictedWinnerTeamId === teamAId
    ? prediction.confidence
    : 1 - prediction.confidence
}

// Formats a probability as a whole percentage with the team name, e.g. "62% Michigan".
export function formatTeamAProbability(probability, teamAName) {
  return `${Math.round(probability * 100)}% ${teamAName}`
}
