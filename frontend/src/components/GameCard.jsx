import ConfidenceGauge from './ConfidenceGauge'
import './GameCard.css'

// Reusable card for a single matchup in the Games List
export default function GameCard({ game, isSelected = false, onSelect }) {
  // Prediction may be missing; use a neutral color when it is
  const pred = game.prediction
  const accentColor = pred ? pred.color : '#3182ce'

  // Native buttons support Tab, Enter, and Space; pressed state reflects selection.
  return (
    <button
      type="button"
      aria-pressed={isSelected}
      onClick={() => onSelect(game)}
      style={{
        display: 'block',
        width: '100%',
        textAlign: 'left',
        color: 'inherit',
        padding: '16px',
        marginBottom: '16px',
        borderRadius: '12px',
        cursor: 'pointer',
        border: isSelected ? `2px solid ${accentColor}` : '1px solid #e2e8f0',
        backgroundColor: '#ffffff',
        boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
        transition: 'all 0.2s ease',
      }}
    >
      <span
        className="game-card-block"
        style={{
          display: 'grid',
          gridTemplateColumns: game.isFinal
            ? 'minmax(0, 1fr) 110px'
            : '1fr auto 110px',
          alignItems: 'center',
          gap: '12px',
        }}
      >
        {/* Matchup row */}
        <span
          className="game-card-block"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '12px',
          }}
        >
          <span
            className="game-card-block"
            title={`${game.awayTeam.name} logo placeholder`}
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
          </span>
          <span className="game-card-block">
            <span
              className="game-card-block"
              style={{
                fontWeight: '800',
                fontSize: '1rem',
                color: '#1a202c',
                lineHeight: '1.2',
              }}
            >
              {game.awayTeam.name} {game.isNeutralSite ? 'vs.' : '@'}{' '}
              {game.homeTeam.name}
            </span>
            <span
              className="game-card-block"
              style={{
                fontSize: '0.75rem',
                color: '#a0aec0',
                marginTop: '2px',
                lineHeight: '1.3',
              }}
            >
              {game.isFinal ? (
                <>
                  {[game.awayTeam, game.homeTeam].map((team) => (
                    <span
                      className="game-card-block"
                      key={team.id}
                      style={{ fontWeight: team.outcome === 'W' ? 700 : 400 }}
                    >
                      {team.name} - {team.score ?? 'Score unavailable'}
                      {team.outcome && ` ${team.outcome}`}
                    </span>
                  ))}
                </>
              ) : (
                <>
                  {game.awayTeam.ranking != null &&
                    `#${game.awayTeam.ranking} `}
                  {game.awayTeam.name}
                  {game.awayTeam.record && ` (${game.awayTeam.record})`}
                  <br />
                  {game.homeTeam.ranking != null &&
                    `#${game.homeTeam.ranking} `}
                  {game.homeTeam.name}
                  {game.homeTeam.record && ` (${game.homeTeam.record})`}
                </>
              )}
            </span>
          </span>
        </span>

        {/* Predicted winner and confidence, or a label if unavailable */}
        {!game.isFinal &&
          (pred?.percentage != null ? (
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
          ))}

        {/* Game Date and Time header */}
        <span
          className="game-card-block"
          style={{
            textAlign: 'right',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'flex-end',
            gap: '4px',
          }}
        >
          <span
            className="game-card-block"
            style={{
              fontSize: '0.8rem',
              fontWeight: '700',
              color: '#2d3748',
            }}
          >
            {game.dateTime.split('•')[0]}
          </span>
          <span
            className="game-card-block"
            style={{ fontSize: '0.75rem', color: '#a0aec0' }}
          >
            {game.dateTime.split('•')[1] || ''}
          </span>
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
        </span>
      </span>
    </button>
  )
}
