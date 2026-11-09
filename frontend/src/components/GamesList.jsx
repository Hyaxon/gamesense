import GameCard from './GameCard'

// Renders the upcoming games as a list of GameCards
export default function GamesList({
  games,
  selectedGameId,
  onSelectGame,
  emptyMessage = 'No upcoming games found.',
}) {
  return (
    <div style={{ marginTop: '16px' }}>
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
        <p>{emptyMessage}</p>
      )}
    </div>
  )
}
