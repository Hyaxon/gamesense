import { useState } from 'react'
import GamesList from '../components/GamesList'
import { usePastGames } from '../hooks/usePastGames'
import { getCurrentSeason } from '../utils/currentSeason'
import './GamesPage.css'

// Coordinates list request states and selection; fetching stays in the query hook.
export default function GameHistoryPage() {
  const season = getCurrentSeason()
  const {
    data: games,
    isPending,
    isError,
    isFetching,
    refetch,
  } = usePastGames(season)

  const [selectedGameId, setSelectedGameId] = useState(null)

  const handleCardClick = (game) => {
    setSelectedGameId(game.id)
  }

  return (
    <div className="games-page">
      <h1 className="games-page-title">Game History</h1>
      <section className="games-region" aria-labelledby="past-games-title">
        <h2 id="past-games-title">Past Games — {season} Season</h2>
        <p className="section-subheading">Final scores, most recent first</p>
        {/* Distinguish absent data from a successful empty list. Keep cached cards
            visible during background refreshes, including failed refreshes. */}
        {isPending ? (
          <p role="status">
            {isFetching ? 'Loading past games…' : 'Waiting for a connection…'}
          </p>
        ) : games === undefined ? (
          <div role="alert">
            <p>Unable to load past games.</p>
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
              emptyMessage={`No completed games found for the ${season} season.`}
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
        <p>Game breakdown integration is coming later.</p>
      </section>
    </div>
  )
}
