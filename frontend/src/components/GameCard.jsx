import { useState } from 'react'
import ConfidenceGauge from './ConfidenceGauge'

// Reusable card for a single matchup in the Games List
export default function GameCard({ game, isSelected, onSelect }) {
// State tracking whether this card's details are expanded
  const [isExpanded, setIsExpanded] = useState(false)
// Fall back to a default prediction when none is provided
  const pred = game.prediction || {
    percentage: 50,
    winnerLogo: game.awayTeam.logo,
    color: '#3182ce',
  }

  // Clicking anywhere on the card selects this game
  return (
    <div
      className={`game-list-item ${isSelected ? 'selected' : ''}`}
      onClick={() => onSelect(game)}
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
        {/* Matchup row */}
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

        {/* Predicted winner and confidence */}
        <ConfidenceGauge
          percentage={pred.percentage}
          logo={pred.winnerLogo}
          color={pred.color}
        />

        {/* Game Date and Time header */}
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
          {/* Toggle details button */}
          <span
            onClick={(e) => {
              e.stopPropagation()
              setIsExpanded(!isExpanded)
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

      {/* EXPANDED DETAILS: Only shows when toggled via Details button */}
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
}