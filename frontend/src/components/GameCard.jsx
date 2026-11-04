import ConfidenceGauge from './ConfidenceGauge'

// Reusable card for a single matchup in the Games List
export default function GameCard({ game, isSelected, onSelect }) {

  // Prediction may be missing; use a neutral color when it is
  const pred = game.prediction
  const accentColor = pred ? pred.color : '#3182ce'

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
          ? `2px solid ${accentColor}`
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
              border: `1px solid ${accentColor}40`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: '800',
              fontSize: '1rem',
              color: accentColor,
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

        {/* Predicted winner and confidence, or a label if unavailable */}
        {pred ? (
          <ConfidenceGauge
            percentage={pred.percentage}
            logo={pred.winnerLogo}
            color={pred.color}
          />
        ) : (
          <span
            style={{
              width: '64px',
              textAlign: 'center',
              fontSize: '0.7rem',
              fontWeight: '600',
              color: '#718096',
            }}
          >
            Prediction not available
          </span>
        )}

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
          {/* Visual cue that the card is clickable */}
          <span
            style={{
              fontSize: '0.75rem',
              fontWeight: '600',
              color: '#a0aec0',
              marginTop: '4px',
              cursor: 'pointer',
            }}
          >
            More Details &gt;
          </span>
        </div>
      </div>
    </div>
  )
}