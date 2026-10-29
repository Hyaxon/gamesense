import { useState } from 'react'
import './GamesList.css'

export default function GamesList({ games, selectedGameId, onSelectGame }) {
  // State tracking the specific game that has details expanded
  const [expandedGameId, setExpandedGameId] = useState(null)
  // Toggle details button
  const handleToggleDetails = (gameId, e) => {
    e.stopPropagation()
    setExpandedGameId(expandedGameId === gameId ? null : gameId)
  }

  return (
    <div className="upcoming-games-container">
      <h2>Upcoming Games</h2>
      <div className="games-list">
        {games && games.length > 0 ? (
          games.map((game) => {
            const isSelected = game.id === selectedGameId
            const isExpanded = game.id === expandedGameId // Check if *this* game's details are open

            return (
              <div
                key={game.id}
                className={`game-card ${isSelected ? 'selected' : ''}`}
                onClick={() => onSelectGame(game)}
              >
                {/* Game Date and Time header */}
                <div className="game-datetime">{game.dateTime}</div>

                {/* Matchup row */}
                <div className="matchup-row">
                  <div className="team-info">
                    <span className="team-logo">{game.awayTeam.logo}</span>
                    <div>
                      <span className="team-rank">
                        #{game.awayTeam.ranking}
                      </span>
                      <span className="team-name">{game.awayTeam.name}</span>
                      <span className="team-record">
                        ({game.awayTeam.record})
                      </span>
                    </div>
                  </div>
                  <span className="at-symbol">@</span>
                  <div className="team-info">
                    <span className="team-logo">{game.homeTeam.logo}</span>
                    <div>
                      <span className="team-rank">
                        #{game.homeTeam.ranking}
                      </span>
                      <span className="team-name">{game.homeTeam.name}</span>
                      <span className="team-record">
                        ({game.homeTeam.record})
                      </span>
                    </div>
                  </div>
                </div>

                {game.predictionSummary && (
                  <div className="prediction-summary">
                    <strong>Prediction:</strong> {game.predictionSummary}
                  </div>
                )}

                {/* EXPANDED DETAILS: Only shows when toggled via Details button */}
                {isExpanded && game.predictions && (
                  <div className="model-breakdown-container">
                    <h4>Prediction Model Variants</h4>
                    <div className="model-grid">
                      {game.predictions.map((pred, index) => (
                        <div key={index} className="model-item">
                          <span className="model-method">{pred.method}</span>
                          <span className="model-winner">
                            {pred.predictedWinner}
                          </span>
                          <span className="model-conf">
                            {(pred.confidenceScore * 100).toFixed(0)}%
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Card footer actions and state button */}
                <div className="card-actions">
                  <button
                    className="details-btn"
                    onClick={(e) => handleToggleDetails(game.id, e)}
                  >
                    {isExpanded ? 'Hide Details' : 'More Details'}
                  </button>
                </div>
              </div>
            )
          })
        ) : (
          <p>No upcoming games found.</p>
        )}
      </div>
    </div>
  )
}
