import { useState } from 'react'
import GamesList from '../components/GamesList'
import GameBreakdown from '../components/GameBreakdown'
import './GamesPage.css'

// Mock games for development until the page is connected to the API
const DEFAULT_GAMES = [
  {
    id: 1,
    dateTime: 'Sat, Oct 24 • 6:00 PM',
    stadiumLocation: 'Gaylord Family - Oklahoma Memorial Stadium · Norman, OK',
    awayTeam: {
      name: 'Oklahoma',
      ranking: 11,
      record: '5-2',
      logo: 'OU',
      conference: 'SEC',
    },
    homeTeam: {
      name: 'Mississippi State',
      ranking: 10,
      record: '5-2',
      logo: 'MSST',
      conference: 'SEC',
    },
    stadiumImage:
      'https://www.ou.edu/content/dam/news/images/articles/2025/august/OU%20Football%20Stadium.jpg',
    prediction: { winnerLogo: 'OU', percentage: 58, color: '#841617' }, //removing this line will cause the confidence gauge to show "Prediction not available" for this game
    predictionSummary: 'Oklahoma (58% favor)',
    predictions: [
      {
        method: 'Bradley-Terry',
        predictedWinner: 'Oklahoma',
        confidenceScore: 0.57,
      },
      {
        method: 'Glicko-2',
        predictedWinner: 'Oklahoma',
        confidenceScore: 0.59,
      },
      {
        method: 'Machine Learning',
        predictedWinner: 'Oklahoma',
        confidenceScore: 0.62,
      },
      { method: 'ELO', predictedWinner: 'Oklahoma', confidenceScore: 0.55 },
      {
        method: 'Monte Carlo',
        predictedWinner: 'Oklahoma',
        confidenceScore: 0.58,
      },
    ],
  },
  {
    id: 2,
    dateTime: 'Sat, Oct 24 • 12:00 PM',
    stadiumLocation: 'Autzen Stadium · Eugene, OR',
    awayTeam: {
      name: 'Michigan',
      ranking: 12,
      record: '5-2',
      logo: 'MICH',
      conference: 'Big Ten',
    },
    homeTeam: {
      name: 'Oregon',
      ranking: 4,
      record: '7-0',
      logo: 'ORE',
      conference: 'Big Ten',
    },
    stadiumImage:
      'https://www.collegegridirons.com/wp-content/uploads/2017/04/autzen17950-600x338.jpg',
    prediction: { winnerLogo: 'ORE', percentage: 61, color: '#154734' }, //removing this line will cause the confidence gauge to show "Prediction not available" for this game
    predictionSummary: 'Oregon (61% favor)',
    predictions: [
      {
        method: 'Bradley-Terry',
        predictedWinner: 'Oregon',
        confidenceScore: 0.6,
      },
      { method: 'Glicko-2', predictedWinner: 'Oregon', confidenceScore: 0.62 },
      {
        method: 'Machine Learning',
        predictedWinner: 'Oregon',
        confidenceScore: 0.64,
      },
      { method: 'ELO', predictedWinner: 'Oregon', confidenceScore: 0.59 },
      {
        method: 'Monte Carlo',
        predictedWinner: 'Oregon',
        confidenceScore: 0.61,
      },
    ],
  },
]

export default function GamesPage() {
  const games = DEFAULT_GAMES
  // Currently selected game, shared with the Game Breakdown
  const [selectedGameId, setSelectedGameId] = useState(DEFAULT_GAMES[0].id)
  // Full game object for the Game Breakdown
  const selectedGame = games.find((game) => game.id === selectedGameId)

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
      <section
        className="games-region game-breakdown-region"
        aria-labelledby="game-breakdown-title"
      >
        <h2 id="game-breakdown-title">Game Breakdown</h2>
        <GameBreakdown game={selectedGame} />
      </section>
    </div>
  )
}
