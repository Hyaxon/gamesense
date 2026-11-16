import { usePastGames } from '../hooks/usePastGames'
import { getCurrentSeason } from '../utils/currentSeason'
import {
  PredictionState,
  consensusOutcome,
  summarizeConsensus,
  summarizeModels,
} from '../utils/predictions'
import './GamesPage.css'

const formatPercent = (value) => `${(value * 100).toFixed(1)}%`

function SummaryCards({ games }) {
  const summary = summarizeConsensus(games)
  const hasConsensus = summary.scored > 0
  return (
    <div className="summary-cards">
      <div className="panel summary-card">
        <p className="summary-label">Games reviewed</p>
        <p className="summary-value">{summary.reviewed}</p>
        <p className="summary-hint">Completed games in this view</p>
      </div>
      <div className="panel summary-card">
        <p className="summary-label">Correct consensus picks</p>
        {hasConsensus ? (
          <p className="summary-value">
            {summary.correct} of {summary.scored}
          </p>
        ) : (
          <p className="summary-value is-unavailable">Consensus unavailable</p>
        )}
        <p className="summary-hint">Predicted the winning team</p>
      </div>
      <div className="panel summary-card">
        <p className="summary-label">Consensus accuracy</p>
        {hasConsensus ? (
          <p className="summary-value is-positive">
            {formatPercent(summary.accuracy)}
          </p>
        ) : (
          <p className="summary-value is-unavailable">Consensus unavailable</p>
        )}
        <p className="summary-hint">Correct winners ÷ games reviewed</p>
      </div>
    </div>
  )
}

// Pick and outcome cells distinguish games with no stored prediction from games
// still waiting for a consensus.
function PickCells({ game }) {
  const { state, consensus } = game.predictions
  if (state === PredictionState.NONE) {
    return (
      <>
        <td className="muted">No pregame prediction</td>
        <td className="muted">No pregame prediction</td>
      </>
    )
  }
  if (!consensus) {
    return (
      <>
        <td className="muted">Consensus unavailable</td>
        <td />
      </>
    )
  }
  const outcome = consensusOutcome(game)
  return (
    <>
      <td>
        <span className="cell-main">
          {consensus.teamName} · {consensus.percentage}%
        </span>
        <span className="cell-sub">Win probability</span>
      </td>
      <td>
        {outcome && (
          <span className={`outcome-badge outcome-${outcome.toLowerCase()}`}>
            {outcome === 'CORRECT' ? 'Correct' : 'Missed'}
          </span>
        )}
      </td>
    </>
  )
}

function CompletedGamesTable({ games }) {
  return (
    <section className="panel" aria-labelledby="completed-games-title">
      <h2 id="completed-games-title">Completed games</h2>
      <p className="section-subheading">
        Compare the pregame consensus pick with what happened on the field.
      </p>
      {games.length === 0 ? (
        <p className="games-empty">No completed games found.</p>
      ) : (
        <>
          <div className="table-scroll">
            <table className="history-table">
              <thead>
                <tr>
                  <th scope="col">Date</th>
                  <th scope="col">Matchup</th>
                  <th scope="col">Final score</th>
                  <th scope="col">Pregame pick</th>
                  <th scope="col">Outcome</th>
                  <th scope="col">Details</th>
                </tr>
              </thead>
              <tbody>
                {games.map((game) => (
                  <tr key={game.id}>
                    <td>
                      <span className="cell-main">{game.date}</span>
                      <span className="cell-sub">Final</span>
                    </td>
                    <td>
                      <span className="history-matchup">
                        {/* PLACEHOLDER: text badge until team logo images exist */}
                        <span className="team-logo-badge" aria-hidden="true">
                          {game.awayTeam.logo}
                        </span>
                        <span>
                          <span className="cell-main">
                            {game.awayTeam.name} vs {game.homeTeam.name}
                          </span>
                          <span className="cell-sub">
                            {[
                              game.week != null && `Week ${game.week}`,
                              game.conferenceLabel,
                            ]
                              .filter(Boolean)
                              .join(' · ')}
                          </span>
                        </span>
                      </span>
                    </td>
                    <td>
                      <span className="cell-main">
                        {game.awayTeam.score ?? '–'} –{' '}
                        {game.homeTeam.score ?? '–'}
                      </span>
                      <span className="cell-sub">Score in matchup order</span>
                    </td>
                    <PickCells game={game} />
                    <td>
                      {/* PLACEHOLDER: recap content arrives with #73 */}
                      <span
                        className="muted"
                        title="Game recaps are not available yet"
                      >
                        View recap →
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="table-footer">
            Showing all {games.length} completed games
          </p>
        </>
      )}
    </section>
  )
}

function ModelPerformance({ games }) {
  const models = summarizeModels(games)
  return (
    <section aria-labelledby="model-performance-title">
      <h2 id="model-performance-title" className="section-title">
        Model performance
      </h2>
      {models.length === 0 ? (
        <p className="panel games-empty">
          No completed games have a stored pregame prediction yet.
        </p>
      ) : (
        <div className="summary-cards">
          {models.map((model) => (
            <div className="panel summary-card" key={model.modelId}>
              <div className="model-card-header">
                <p className="model-card-name">{model.name}</p>
                <p className="summary-label">
                  {model.correct} of {model.total} correct
                </p>
              </div>
              <p className="summary-value is-positive">
                {formatPercent(model.correct / model.total)}
              </p>
              <p className="summary-hint">
                Winner-pick accuracy across {model.total} completed games
              </p>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}

// Coordinates the completed-games request states; fetching stays in the query hook.
export default function GameHistoryPage() {
  const season = getCurrentSeason()
  const {
    data: games,
    isPending,
    isError,
    isFetching,
    refetch,
  } = usePastGames(season)

  return (
    <div className="games-page">
      <header className="page-header">
        <h1>Game History</h1>
        <p>See how past predictions compare with the final results.</p>
      </header>

      {/* Distinguish absent data from a successful empty list. Keep cached data
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
          {isError && (
            <div role="alert">
              <p>Could not refresh games. Showing previously loaded data.</p>
              <button onClick={() => refetch()}>Try again</button>
            </div>
          )}
          <SummaryCards games={games} />
          <CompletedGamesTable games={games} />
          <ModelPerformance games={games} />
        </>
      )}
    </div>
  )
}
