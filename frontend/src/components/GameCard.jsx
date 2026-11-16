import ConfidenceGauge from './ConfidenceGauge'
import { PredictionState } from '../utils/predictions'
import './GameCard.css'

// What fills the consensus slot when there is no consensus to show.
const CONSENSUS_PLACEHOLDER = {
  [PredictionState.PENDING]: 'Calculating predictions',
  [PredictionState.AVAILABLE]: 'Consensus unavailable',
  [PredictionState.UNAVAILABLE]: 'Predictions unavailable',
  [PredictionState.NONE]: 'Predictions unavailable',
}

// One line per team: conference and name. Rankings and records (#117) are
// added here once the API supplies them.
function teamLine(team) {
  return team.conference ? `${team.conference} - ${team.name}` : team.name
}

// Reusable row for a single upcoming matchup in the Games list.
export default function GameCard({ game, isSelected = false, onSelect }) {
  const { consensus, state } = game.predictions

  // Native buttons support Tab, Enter, and Space; pressed state reflects selection.
  return (
    <button
      type="button"
      className={`game-card${isSelected ? ' is-selected' : ''}`}
      aria-pressed={isSelected}
      onClick={() => onSelect(game)}
    >
      {/* PLACEHOLDER: text badge until team logo images exist */}
      <span
        className="team-logo-badge"
        title={`${game.homeTeam.name} logo placeholder`}
        aria-hidden="true"
      >
        {game.homeTeam.logo}
      </span>

      <span className="game-card-matchup">
        <span className="game-card-title">
          {game.awayTeam.name} {game.isNeutralSite ? 'vs.' : '@'}{' '}
          {game.homeTeam.name}
        </span>
        <span className="game-card-team">{teamLine(game.awayTeam)}</span>
        <span className="game-card-team">{teamLine(game.homeTeam)}</span>
      </span>

      <span className="game-card-consensus">
        {consensus ? (
          <ConfidenceGauge
            percentage={consensus.percentage}
            label={consensus.abbreviation}
            side={consensus.side}
          />
        ) : (
          <span className="consensus-placeholder">
            {CONSENSUS_PLACEHOLDER[state]}
          </span>
        )}
      </span>

      <span className="game-card-schedule">
        <span className="game-card-date">{game.date}</span>
        {game.time && <span className="game-card-time">{game.time}</span>}
        <span className="game-card-action">
          {isSelected ? 'Selected' : 'View game →'}
        </span>
      </span>
    </button>
  )
}
