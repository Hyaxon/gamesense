import PredictionRow from './PredictionRow'

/** Renders the detailed game breakdown view of a selected football matchup 
NOTES: 
- Stadium Image: Uses default Unsplash fallback with `game.stadiumImage` missing.
- Team Logos: Text badges (`game.awayTeam.logo`) act as placeholders for future image assets. */

export default function GameBreakdown({ game }) {
  // Renders empty state placeholder when no game is active
  if (!game?.awayTeam || !game.homeTeam) {
    return (
      <div className="game-breakdown empty-state">
        <p>
          Select a matchup from the list to view detailed model predictions and
          game insights.
        </p>
      </div>
    )
  }

  // Format prediction output and assign team color classes
  const formatPred = (winnerName, score) => {
    if (!winnerName) return { text: '--', teamClass: '' }
    const isAway =
      winnerName.trim().toLowerCase() === game.awayTeam.name.toLowerCase()
    const logo = isAway ? game.awayTeam.logo : game.homeTeam.logo
    const percent = Math.round(score > 1 ? score : score * 100)
    return {
      text: `${percent}% ${logo}`,
      teamClass: isAway ? 'away-team-color' : 'home-team-color',
    }
  }

  const presageWinner =
    game.predictionSummary?.split(' ')[0] || game.awayTeam.name
  const presageFormatted = formatPred(presageWinner, 0.67)
  const venueLocation = game.stadiumLocation || `${game.homeTeam.name} Stadium`

  return (
    <div className="game-breakdown">
      <div
        className="game-hero-banner"
        style={{
          backgroundImage: `linear-gradient(180deg, rgba(0,0,0,0.1) 0%, rgba(0,0,0,0.4) 100%), url('${game.stadiumImage || 'https://images.unsplash.com/photo-1508098682722-e99c43a406b2?auto=format&fit=crop&w=1200&q=80'}')`,
        }}
      />

      <div className="breakdown-meta-header">
        <span className="breakdown-eyebrow">GAME BREAKDOWN</span>
        <h2 className="breakdown-title">
          {game.awayTeam.name} @ {game.homeTeam.name}
        </h2>
        <div className="breakdown-sub-info">
          <span className="game-datetime">{game.dateTime}</span>
          <span>•</span>
          <span className="stadium-location">{venueLocation}</span>
        </div>
      </div>

      <div className="matchup-header-card">
        {/* Away Team */}
        <div className="team-col">
          <div className="logo-rank-row">
            <span className="team-rank">#{game.awayTeam.ranking ?? ''}</span>
            {/* PLACEHOLDER: Swap text element for <img> when image assets are ready */}
            <div className="team-logo-badge away-logo">
              {game.awayTeam.logo}
            </div>
          </div>
          <span className="team-name">{game.awayTeam.name}</span>
          <span className="team-record">{game.awayTeam.record}</span>
        </div>

        <div className="vs-divider-box">
          <div className="vs-line"></div>
          <span className="vs-text">VS</span>
          <div className="vs-line"></div>
        </div>

        {/* Home Team */}
        <div className="team-col">
          <div className="logo-rank-row">
            <span className="team-rank">#{game.homeTeam.ranking ?? ''}</span>
            {/* PLACEHOLDER: Swap text element for <img> when image assets are ready */}
            <div className="team-logo-badge home-logo">
              {game.homeTeam.logo}
            </div>
          </div>
          <span className="team-name">{game.homeTeam.name}</span>
          <span className="team-record">{game.homeTeam.record}</span>
        </div>
      </div>

      {/* Prediction Models List */}
      <div className="model-predictions-section">
        <div className="model-section-header">
          <h4>
            Prediction Models <span className="info-icon">ⓘ</span>
          </h4>
          <span className="model-accuracy-label">
            Model Accuracy <span className="info-icon">ⓘ</span>
          </span>
        </div>

        <div className="prediction-scroll-container">
          <div className="prediction-rows-list">
            {/* Presage Summary Row (Weighted Average and dynamically changes to hightlight as predicted winning team color) */}
            <PredictionRow
              isPresage={true}
              icon="♞"
              modelName="Presage Prediction"
              description="Weighted average of all models"
              formattedPrediction={presageFormatted}
              cardColor={game.prediction?.color}
            />

            {/* Algorithm Rows */}
            {game.predictions?.map((pred, i) => (
              <PredictionRow
                key={i}
                icon="♞"
                modelName={pred.method?.replace(':', '').trim() || 'Model'}
                description={getModelDesc(pred.method)}
                formattedPrediction={formatPred(
                  pred.predictedWinner,
                  pred.confidenceScore,
                )}
                tier={getTier(pred.confidenceScore)}
              />
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}

const getModelDesc = (m = '') => {
  const lower = m.toLowerCase()
  if (lower.includes('bradley') || lower.includes('elo'))
    return 'Ratings-based model'
  if (lower.includes('glicko')) return 'Dynamic ratings model'
  if (lower.includes('machine')) return 'AI prediction model'
  if (lower.includes('monte')) return 'Random chance baseline'
  return 'Prediction model'
}

const getTier = (score = 0) => {
  if (score >= 0.56) return { label: 'High', className: 'tier-high' }
  if (score >= 0.53) return { label: 'Medium', className: 'tier-medium' }
  return { label: 'Low', className: 'tier-low' }
}
