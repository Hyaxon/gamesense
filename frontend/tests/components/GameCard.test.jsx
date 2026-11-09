import { renderToStaticMarkup } from 'react-dom/server'
import { expect, it } from 'vitest'
import GameCard from '../../src/components/GameCard'
import { mapPastGameToCard } from '../../src/utils/mapGameToCard'

const GAME = {
  id: 'game',
  scheduledAt: '2026-10-01T18:00:00Z',
  awayTeamId: 'a',
  homeTeamId: 'b',
  awayTeam: { name: 'Team A', shortName: 'TA' },
  homeTeam: { name: 'Team B', shortName: 'TB' },
  awayScore: 30,
  homeScore: 0,
  winnerTeamId: 'a',
}

it('shows scores and W/L in the original card with no prediction UI', () => {
  const html = renderToStaticMarkup(
    <GameCard game={mapPastGameToCard(GAME)} onSelect={() => {}} />,
  )
  expect(html).toContain('Team A - 30 W')
  expect(html).toContain('Team B - 0 L')
  expect(html).toContain('>TA</div>')
  expect(html).toContain('More Details')
  expect(html).not.toContain('<svg')
  expect(html).not.toContain('Prediction not available')
})

it('preserves the prediction gauge for upcoming cards', () => {
  const game = {
    ...mapPastGameToCard(GAME),
    isFinal: false,
    prediction: { percentage: 60, winnerLogo: 'TA', color: '#123456' },
  }
  const html = renderToStaticMarkup(
    <GameCard game={game} onSelect={() => {}} />,
  )
  expect(html).toContain('<svg')
  expect(html).toContain('60%')
  expect(html).not.toContain('30 W')
})

it('does not fabricate wins or scores when results are unavailable', () => {
  const card = mapPastGameToCard({
    ...GAME,
    winnerTeamId: null,
    awayScore: null,
    homeScore: null,
  })
  expect(card.awayTeam.outcome).toBeNull()
  const html = renderToStaticMarkup(
    <GameCard game={card} onSelect={() => {}} />,
  )
  expect(html).toContain('Score unavailable')
  expect(html).not.toContain(' W')
})

it('labels equal final scores as ties without a winner', () => {
  const card = mapPastGameToCard({
    ...GAME,
    winnerTeamId: null,
    awayScore: 0,
    homeScore: 0,
  })
  expect(card.awayTeam.outcome).toBe('T')
  expect(card.homeTeam.outcome).toBe('T')
})
