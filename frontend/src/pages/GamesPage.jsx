import { useState } from 'react'
import GamesList from '../components/GamesList'
// import GameBreakdown from '../components/GameBreakdown'
import { useUpcomingGames } from '../hooks/useUpcomingGames'
import './GamesPage.css'

// Coordinates list request states and selection; fetching stays in the query hook.
export default function GamesPage() {
  const {
    data: games,
    isPending,
    isError,
    isFetching,
    refetch,
  } = useUpcomingGames()

  const [selectedGameId, setSelectedGameId] = useState(null)

  // Restore when Game Breakdown is connected to backend data.
  // const selectedGame = games?.find((game) => game.id === selectedGameId)

  const handleCardClick = (game) => {
    setSelectedGameId(game.id)
  }

  return (
    <div className="games-page">
      <h1 className="games-page-title">Games</h1>
      <section className="games-region" aria-labelledby="upcoming-games-title">
        <h2 id="upcoming-games-title">Upcoming Games</h2>
        <p className="section-subheading">
          Predictions provided by multiple prediction models
        </p>
        {/* Distinguish absent data from a successful empty list. Keep cached cards
            visible during background refreshes, including failed refreshes. */}
        {isPending ? (
          <p role="status">
            {isFetching
              ? 'Loading upcoming games…'
              : 'Waiting for a connection…'}
          </p>
        ) : games === undefined ? (
          <div role="alert">
            <p>Unable to load upcoming games.</p>
            <button onClick={() => refetch()}>Try again</button>
          </div>
        ) : (
          <>
            {isFetching && <p role="status">Updating games…</p>}

            {isError && (
              <div role="alert">
                <p>Could not refresh games. Showing previously loaded data.</p>
                <button onClick={() => refetch()}>Try again</button>
              </div>
            )}

            <GamesList
              games={games}
              selectedGameId={selectedGameId}
              onSelectGame={handleCardClick}
            />
          </>
        )}
      </section>
      <section
        className="games-region game-breakdown-region"
        aria-labelledby="game-breakdown-title"
      >
        <h2 id="game-breakdown-title">Game Breakdown</h2>
        {/* <GameBreakdown game={selectedGame} /> */}
        <p>Game breakdown integration is coming later.</p>
      </section>
    </div>
  )
}
