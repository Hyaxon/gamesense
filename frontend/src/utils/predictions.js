// Display text for each prediction method. The API supplies model names; these
// add the short description and icon shown in the Game Breakdown.
const METHOD_DETAILS = {
  ELO: { description: 'Ratings-based model', icon: '♞' },
  GLICKO2: { description: 'Dynamic ratings model', icon: '✦' },
  BRADLEY_TERRY: { description: 'Paired-comparison model', icon: '⚖' },
  TRUESKILL: { description: 'Ratings-based model', icon: '♞' },
  MONTE_CARLO: { description: 'Simulation model', icon: '⟳' },
  MACHINE_LEARNING: { description: 'AI prediction model', icon: '⚙' },
  RANDOM: { description: 'Random chance baseline', icon: '◐' },
  HOME_GROWN: { description: 'Custom model', icon: '★' },
}

const FALLBACK_DETAILS = { description: 'Prediction model', icon: '♞' }

/** Prediction states from the API, plus NONE when a game was never predicted. */
export const PredictionState = Object.freeze({
  NONE: 'NONE',
  PENDING: 'PENDING',
  AVAILABLE: 'AVAILABLE',
  UNAVAILABLE: 'UNAVAILABLE',
})

// Resolve the predicted winner to one of the two mapped teams.
function pick(winnerTeamId, confidence, homeTeam, awayTeam) {
  const side = winnerTeamId === homeTeam.id ? 'home' : 'away'
  const team = side === 'home' ? homeTeam : awayTeam
  return {
    teamId: winnerTeamId,
    side,
    teamName: team.name,
    abbreviation: team.logo,
    percentage: Math.round(confidence * 100),
  }
}

/**
 * Maps a GameResponse `predictions` object for display. Models keep the API
 * order. The consensus is present only when the API returns an aggregate.
 */
export function mapPredictions(predictions, homeTeam, awayTeam) {
  if (!predictions) {
    return { state: PredictionState.NONE, models: [], consensus: null }
  }
  const models = (predictions.models ?? []).map((model) => {
    const details = METHOD_DETAILS[model.prediction.method] ?? FALLBACK_DETAILS
    return {
      modelId: model.modelId,
      name: model.displayName,
      method: model.prediction.method,
      ...details,
      ...pick(
        model.prediction.predictedWinnerTeamId,
        model.prediction.confidence,
        homeTeam,
        awayTeam,
      ),
    }
  })
  const aggregate = predictions.aggregate
  return {
    state: predictions.state,
    models,
    consensus: aggregate
      ? pick(
          aggregate.predictedWinnerTeamId,
          aggregate.confidence,
          homeTeam,
          awayTeam,
        )
      : null,
  }
}

/** True while any game's predictions are still being calculated. */
export function hasPendingPredictions(games) {
  return (
    games?.some(
      (game) => game.predictions?.state === PredictionState.PENDING,
    ) ?? false
  )
}

/**
 * TanStack Query refetch interval: poll every five seconds while the backend is
 * still calculating startup predictions. Checks the raw API data, before select.
 */
export function pollWhilePending(query) {
  return hasPendingPredictions(query.state.data) ? 5000 : false
}

// A pick can only be scored against a game with a winner; ties are left out.
function isCorrect(pickTeamId, game) {
  return game.winnerTeamId ? pickTeamId === game.winnerTeamId : null
}

/** Correct or missed consensus pick for a completed game, or null. */
export function consensusOutcome(game) {
  const consensus = game.predictions.consensus
  if (!consensus) return null
  const correct = isCorrect(consensus.teamId, game)
  if (correct === null) return null
  return correct ? 'CORRECT' : 'MISSED'
}

/** Summary card figures; consensus figures are null until an aggregate exists. */
export function summarizeConsensus(games) {
  const scored = games.filter((game) => consensusOutcome(game) !== null)
  const correct = scored.filter(
    (game) => consensusOutcome(game) === 'CORRECT',
  ).length
  return {
    reviewed: games.length,
    scored: scored.length,
    correct,
    accuracy: scored.length > 0 ? correct / scored.length : null,
  }
}

/**
 * Counts each model's correct winner picks across completed games that have a
 * stored prediction from that model. Models appear in first-seen order.
 */
export function summarizeModels(games) {
  const byModel = new Map()
  for (const game of games) {
    for (const model of game.predictions.models) {
      const correct = isCorrect(model.teamId, game)
      if (correct === null) continue
      const summary = byModel.get(model.modelId) ?? {
        modelId: model.modelId,
        name: model.name,
        correct: 0,
        total: 0,
      }
      summary.total += 1
      if (correct) summary.correct += 1
      byModel.set(model.modelId, summary)
    }
  }
  return [...byModel.values()]
}
