import GameCard from './GameCard'

// Renders the upcoming games as a list of GameCards
export default function GamesList({
  games,
  selectedGameId,
  onSelectGame,
  emptyMessage = 'No upcoming games found.',
}) {
  // One card per game, or a message if there are none
  if (!games || games.length === 0) {
    return <p className="games-empty">{emptyMessage}</p>
  }
  return (
    <ul className="games-list">
      {games.map((game) => (
        <li key={game.id}>
          <GameCard
            game={game}
            isSelected={game.id === selectedGameId}
            onSelect={onSelectGame}
          />
        </li>
      ))}
    </ul>
  )
}
