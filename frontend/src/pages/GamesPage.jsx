import './GamesPage.css'

function GamesPage() {
  return (
    <div className="games-page">
      <h1 className="games-page-title">Games</h1>
      <section className="games-region" aria-labelledby="upcoming-games-title">
        <h2 id="upcoming-games-title">Upcoming Games</h2>
        <p className="section-subheading">
          Predictions provided by multiple prediction models
        </p>
        {/* The Games List component will render here. */}
      </section>
      <section className="games-region" aria-labelledby="game-breakdown-title">
        <h2 id="game-breakdown-title">Game Breakdown</h2>
        {/* The Game Breakdown component will render here. */}
      </section>
    </div>
  )
}

export default GamesPage
