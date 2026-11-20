import PredictionRow from './PredictionRow'
import {
  formatTeamAProbability,
  getAccuracyTier,
  getModelDesc,
  getModelName,
  teamAWinProbability,
} from '../utils/predictionMethods'
import './PredictionMethods.css'

/** Prediction Methods section: every model's win probability for Team A.
 * - teamA: { id, name }. Every probability is shown from this team's side.
 * - status: 'empty' | 'loading' | 'error' | 'ready'.
 * - predictions / aggregate: shaped like PredictionResponse.predictions / .aggregate.
 * - tiers: accuracy tier by method (plus CONSENSUS), e.g. { ELO: 'HIGH' }. A missing tier shows no badge. */
export default function PredictionMethods({
  teamA,
  status,
  predictions = [],
  aggregate,
  tiers = {},
  errorMessage = 'Predictions could not be generated. Please try again.',
}) {
  // Builds one row from a result that has a predictedWinnerTeamId and confidence.
  const toRow = (key, result, name, description, tier) => (
    <PredictionRow
      key={key}
      isPresage={key === 'consensus'}
      icon="♞"
      modelName={name}
      description={description}
      formattedPrediction={{
        text: formatTeamAProbability(
          teamAWinProbability(result, teamA.id),
          teamA.name,
        ),
      }}
      tier={getAccuracyTier(tier)}
    />
  )

  const rows = []
  if (aggregate) {
    rows.push(
      toRow(
        'consensus',
        aggregate,
        'Consensus Model',
        'Combined result of all models',
        tiers.CONSENSUS,
      ),
    )
  }
  predictions.forEach((p) =>
    rows.push(
      toRow(
        p.id,
        p,
        getModelName(p.method),
        getModelDesc(p.method),
        tiers[p.method],
      ),
    ),
  )

  let body
  if (status === 'loading') {
    body = (
      <p className="methods-message" role="status">
        Generating predictions...
      </p>
    )
  } else if (status === 'error') {
    body = (
      <p className="methods-message methods-error" role="alert">
        {errorMessage}
      </p>
    )
  } else if (status !== 'ready' || rows.length === 0) {
    body = (
      <p className="methods-message">
        Generate predictions to compare each model&apos;s result.
      </p>
    )
  } else {
    body = (
      <>
        <div className="methods-header">
          {[0, 1].map((i) => (
            <div key={i} className="methods-header-columns">
              <span>Model</span>
              <span>{teamA.name} win chance</span>
              <span>Model accuracy</span>
            </div>
          ))}
        </div>
        <div className="methods-grid">{rows}</div>
        <p className="methods-disclaimer">
          Probabilities are estimates, not guarantees. Model accuracy is
          separate from a team&apos;s chance of winning.
        </p>
      </>
    )
  }

  return (
    <div className="prediction-methods">
      <h2 id="methods-title">Prediction methods</h2>
      <p className="region-description">
        Compare each model&apos;s {teamA.name} win probability. Accuracy labels
        describe model performance, not certainty of this result.
      </p>
      {body}
    </div>
  )
}
