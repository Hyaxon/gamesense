import { useState, useEffect } from 'react'
import './GamesPage.css'

const DEFAULT_GAMES = [
  {
    id: 1,
    dateTime: 'Sat, Oct 24 • 6:00 PM',
    awayTeam: { name: 'Oklahoma', ranking: 11, record: '5-2', logo: 'OU' },
    homeTeam: {
      name: 'Mississippi State',
      ranking: 10,
      record: '5-2',
      logo: 'MSST',
    },
    prediction: { winnerLogo: 'OU', percentage: 58, color: '#841617' },
  },
  {
    id: 2,
    dateTime: 'Sat, Oct 24 • 12:00 PM',
    awayTeam: { name: 'Michigan', ranking: 12, record: '5-2', logo: 'MICH' },
    homeTeam: { name: 'Oregon', ranking: 4, record: '7-0', logo: 'ORE' },
    prediction: { winnerLogo: 'ORE', percentage: 61, color: '#154734' },
  },
]

function ConfidenceGauge({ percentage = 50, logo = '', color = '#3182ce' }) {
  const r = 26
  const circ = 2 * Math.PI * r
  const offset = circ - (percentage / 100) * circ

  return (
    <div
      style={{
        position: 'relative',
        width: '64px',
        height: '64px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <svg
        width="64"
        height="64"
        viewBox="0 0 64 64"
        style={{ transform: 'rotate(-90deg)' }}
      >
        <circle
          cx="32"
          cy="32"
          r={r}
          stroke="#edf2f7"
          strokeWidth="5"
          fill="transparent"
        />
        <circle
          cx="32"
          cy="32"
          r={r}
          stroke={color}
          strokeWidth="5"
          strokeDasharray={circ}
          strokeDashoffset={offset}
          strokeLinecap="round"
          fill="transparent"
        />
      </svg>
      <div
        style={{ position: 'absolute', textAlign: 'center', lineHeight: '1.1' }}
      >
        <div
          style={{ fontWeight: '800', fontSize: '0.85rem', color: '#1a202c' }}
        >
          {percentage}%
        </div>
        <div
          style={{
            fontWeight: '700',
            fontSize: '0.65rem',
            color: '#718096',
            textTransform: 'uppercase',
          }}
        >
          {logo}
        </div>
      </div>
    </div>
  )
}

export default function GamesPage() {
  const [games, setGames] = useState(DEFAULT_GAMES)
  const [selectedGameId, setSelectedGameId] = useState(DEFAULT_GAMES[0].id)
  const [expandedCardId, setExpandedCardId] = useState(null)

  useEffect(() => {
    fetch('/api/games')
      .then((res) => (res.ok ? res.json() : Promise.reject()))
      .then((data) => {
        if (Array.isArray(data) && data.length > 0) {
          setGames(data)
          setSelectedGameId(data[0].id)
        }
      })
      .catch(() => {})
  }, [])

  const handleCardClick = (game) => {
    setSelectedGameId(game.id)
  }

  return (
    <div
      className="games-page"
      style={{ maxWidth: '800px', margin: '0 auto', padding: '24px' }}
    >
      <div className="games-region">
        <div className="games-list-container">
          <h3
            style={{
              fontSize: '1.5rem',
              fontWeight: '800',
              margin: '0 0 4px 0',
              color: '#1a202c',
            }}
          >
            UPCOMING GAMES
          </h3>
          <span style={{ fontSize: '0.8rem', color: '#718096' }}>
            Predictions provided by multiple prediction models
          </span>

          <div className="games-list-scroll" style={{ marginTop: '16px' }}>
            {games.map((game) => {
              const isSelected = selectedGameId === game.id
              const isExpanded = expandedCardId === game.id
              const pred = game.prediction || {
                percentage: 50,
                winnerLogo: game.awayTeam.logo,
                color: '#3182ce',
              }

              return (
                <div
                  key={game.id}
                  className={`game-list-item ${isSelected ? 'selected' : ''}`}
                  onClick={() => handleCardClick(game)}
                  style={{
                    padding: '16px',
                    marginBottom: '16px',
                    borderRadius: '12px',
                    cursor: 'pointer',
                    border: isSelected
                      ? `2px solid ${pred.color}`
                      : '1px solid #e2e8f0',
                    backgroundColor: '#ffffff',
                    boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
                    transition: 'all 0.2s ease',
                  }}
                >
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: '1fr auto 110px',
                      alignItems: 'center',
                      gap: '12px',
                    }}
                  >
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '12px',
                      }}
                    >
                      <div
                        style={{
                          width: '44px',
                          height: '44px',
                          borderRadius: '8px',
                          backgroundColor: '#f7fafc',
                          border: `1px solid ${pred.color}40`,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontWeight: '800',
                          fontSize: '1rem',
                          color: pred.color,
                          flexShrink: 0,
                        }}
                      >
                        {game.awayTeam.logo}
                      </div>
                      <div>
                        <div
                          style={{
                            fontWeight: '800',
                            fontSize: '1rem',
                            color: '#1a202c',
                            lineHeight: '1.2',
                          }}
                        >
                          {game.awayTeam.name} @ {game.homeTeam.name}
                        </div>
                        <div
                          style={{
                            fontSize: '0.75rem',
                            color: '#a0aec0',
                            marginTop: '2px',
                            lineHeight: '1.3',
                          }}
                        >
                          #{game.awayTeam.ranking || ''} {game.awayTeam.name} (
                          {game.awayTeam.record})<br />#
                          {game.homeTeam.ranking || ''} {game.homeTeam.name} (
                          {game.homeTeam.record})
                        </div>
                      </div>
                    </div>

                    <ConfidenceGauge
                      percentage={pred.percentage}
                      logo={pred.winnerLogo}
                      color={pred.color}
                    />

                    <div
                      style={{
                        textAlign: 'right',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'flex-end',
                        gap: '4px',
                      }}
                    >
                      <div
                        style={{
                          fontSize: '0.8rem',
                          fontWeight: '700',
                          color: '#2d3748',
                        }}
                      >
                        {game.dateTime.split('•')[0]}
                      </div>
                      <div style={{ fontSize: '0.75rem', color: '#a0aec0' }}>
                        {game.dateTime.split('•')[1] || ''}
                      </div>
                      <span
                        onClick={(e) => {
                          e.stopPropagation()
                          setExpandedCardId(isExpanded ? null : game.id)
                        }}
                        style={{
                          fontSize: '0.75rem',
                          fontWeight: '600',
                          color: '#a0aec0',
                          marginTop: '4px',
                          cursor: 'pointer',
                        }}
                      >
                        {isExpanded ? 'More Details v' : 'More Details >'}
                      </span>
                    </div>
                  </div>

                  {isExpanded && (
                    <div
                      style={{
                        marginTop: '12px',
                        paddingTop: '10px',
                        borderTop: '1px solid #edf2f7',
                        fontSize: '0.8rem',
                        color: '#4a5568',
                        backgroundColor: '#f7fafc',
                        padding: '10px 12px',
                        borderRadius: '8px',
                      }}
                    >
                      <div
                        style={{
                          fontWeight: '700',
                          color: '#1a202c',
                          marginBottom: '6px',
                        }}
                      >
                        {' '}
                        More Details Here:
                      </div>
                      {game.predictions?.map((p, idx) => (
                        <div
                          key={idx}
                          style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            padding: '2px 0',
                          }}
                        >
                          <span>{p.method}:</span>
                          <span style={{ fontWeight: '600' }}>
                            {p.predictedWinner} (
                            {(p.confidenceScore * 100).toFixed(0)}%)
                          </span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        </div>
      </div>
    </div>
  )
}
