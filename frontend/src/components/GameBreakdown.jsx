import PredictionRow from './PredictionRow'
import { PredictionState } from '../utils/predictions'

// Shown instead of model rows when no model results are available.
const MODELS_MESSAGE = {
  [PredictionState.PENDING]:
    'Predictions are being calculated. They will appear here shortly.',
  [PredictionState.UNAVAILABLE]: 'Predictions are unavailable for this game.',
  [PredictionState.NONE]: 'Predictions are unavailable for this game.',
}

function TeamColumn({ team }) {
  // Rankings and records (#117) are added here once the API supplies them.
  return (
    <div className="breakdown-team">
      {/* PLACEHOLDER: text badge until team logo images exist */}
      <span className="team-logo-badge" aria-hidden="true">
        {team.logo}
      </span>
      <span className="breakdown-team-name">{team.name}</span>
    </div>
  )
}

/** Detailed view of the selected game, built from its Games API response
 * without another request. */
export default function GameBreakdown({ game }) {
  if (!game) {
    return (
      <p className="breakdown-empty">
        Select a matchup from the list to view its model predictions.
      </p>
    )
  }

  const { state, models, consensus } = game.predictions
  const schedule = [game.date, game.time].filter(Boolean).join(' · ')

  return (
    <div className="game-breakdown">
      {/* PLACEHOLDER: generic banner until stadium images exist */}
      <div className="breakdown-banner" aria-hidden="true" />

      <p className="breakdown-eyebrow">GAME BREAKDOWN</p>
      <h3 className="breakdown-title">
        {game.awayTeam.name} {game.isNeutralSite ? 'vs.' : '@'}{' '}
        {game.homeTeam.name}
      </h3>
      <p className="breakdown-schedule">{schedule}</p>
      {game.venue && <p className="breakdown-venue">{game.venue}</p>}

      <div className="breakdown-teams">
        <TeamColumn team={game.awayTeam} />
        <span className="breakdown-vs">vs</span>
        <TeamColumn team={game.homeTeam} />
      </div>

      <div className="consensus-box">
        <div>
          <p className="consensus-title">Consensus prediction</p>
          <p className="consensus-subtitle">Weighted average of all models</p>
        </div>
        {consensus ? (
          <p className={`consensus-value team-${consensus.side}`}>
            {consensus.percentage}% {consensus.teamName}
          </p>
        ) : (
          <p className="consensus-unavailable">Consensus unavailable</p>
        )}
      </div>

      <div className="models-header">
        <h4>Prediction models</h4>
        <span className="models-columns">Win chance / Model accuracy</span>
      </div>
      {models.length > 0 ? (
        <ul className="prediction-rows">
          {models.map((model) => (
            <PredictionRow key={model.modelId} model={model} />
          ))}
        </ul>
      ) : (
        <p className="models-message" role="status">
          {MODELS_MESSAGE[state]}
        </p>
      )}
    </div>
  )
}
