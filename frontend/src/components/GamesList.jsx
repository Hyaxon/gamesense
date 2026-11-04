import GameCard from './GameCard'
import './GamesList.css'
// Renders the upcoming games as a list of GameCards
export default function GamesList({ games, selectedGameId, onSelectGame }) {
  return (
    <div className="games-list-scroll" style={{ marginTop: '16px' }}>
      {/* One card per game, or a message if there are none */}
      {games && games.length > 0 ? (
        games.map((game) => (
          <GameCard
            key={game.id}
            game={game}
            isSelected={game.id === selectedGameId}
            onSelect={onSelectGame}
          />
        ))
      ) : (
        <p>No upcoming games found.</p>
      )}
    </div>
  )
}