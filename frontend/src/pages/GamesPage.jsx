import { useState } from 'react'
import GamesList from '../components/GamesList'
import './GamesPage.css'

// Mock games for development until the page is connected to the API
const DEFAULT_GAMES = [
  {
    id: 1,
    dateTime: 'Sat, Oct 24 • 6:00 PM',
    awayTeam: { name: 'Oklahoma', ranking: 11, record: '5-2', logo: 'OU' },
    homeTeam: {
      name: 'Mississippi State',
      ranking: 10,
      record: '5-2',
      logo: 'MSST',
    },
    prediction: { winnerLogo: 'OU', percentage: 58, color: '#841617' }, //removing this line will cause the confidence gauge to show "Prediction not available" for this game
  },
  {
    id: 2,
    dateTime: 'Sat, Oct 24 • 12:00 PM',
    awayTeam: { name: 'Michigan', ranking: 12, record: '5-2', logo: 'MICH' },
    homeTeam: { name: 'Oregon', ranking: 4, record: '7-0', logo: 'ORE' },
    prediction: { winnerLogo: 'ORE', percentage: 61, color: '#154734' }, //removing this line will cause the confidence gauge to show "Prediction not available" for this game
  },
]

export default function GamesPage() {
  const [games] = useState(DEFAULT_GAMES)
  // Currently selected game, shared with the Game Breakdown later
  const [selectedGameId, setSelectedGameId] = useState(DEFAULT_GAMES[0].id)

  // Called when a game card is clicked
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
        <GamesList
          games={games}
          selectedGameId={selectedGameId}
          onSelectGame={handleCardClick}
        />
      </section>
      <section className="games-region" aria-labelledby="game-breakdown-title">
        <h2 id="game-breakdown-title">Game Breakdown</h2>
        {/* The Game Breakdown component will render here. */}
      </section>
    </div>
  )
}