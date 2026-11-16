import { useState } from 'react'
import GameBreakdown from '../components/GameBreakdown'
import GamesList from '../components/GamesList'
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

  // The Breakdown reads the selected game from the list response, so selecting
  // a game needs no request. Show the first game until one is chosen.
  const selectedGame =
    games?.find((game) => game.id === selectedGameId) ?? games?.[0]

  return (
    <div className="games-page">
      <header className="page-header">
        <h1>Games</h1>
        <p>Explore upcoming matchups and compare model predictions.</p>
      </header>

      <div className="games-columns">
        <section className="panel" aria-labelledby="upcoming-games-title">
          <h2 id="upcoming-games-title">Upcoming games</h2>
          <p className="section-subheading">
            Select a matchup to explore its predictions.
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
              {isError && (
                <div role="alert">
                  <p>
                    Could not refresh games. Showing previously loaded data.
                  </p>
                  <button onClick={() => refetch()}>Try again</button>
                </div>
              )}

              <GamesList
                games={games}
                selectedGameId={selectedGame?.id}
                onSelectGame={(game) => setSelectedGameId(game.id)}
              />
            </>
          )}

          <aside className="reading-note" aria-labelledby="reading-note-title">
            <h3 id="reading-note-title">How to read predictions</h3>
            <p>
              The percentage is the predicted win probability for the team
              shown. Model accuracy describes past performance, not a guaranteed
              outcome.
            </p>
          </aside>
        </section>

        <section
          className="panel game-breakdown-region"
          aria-label="Game breakdown"
        >
          <GameBreakdown game={selectedGame} />
        </section>
      </div>
    </div>
  )
}
