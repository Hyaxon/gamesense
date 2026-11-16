import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, expect, it, vi } from 'vitest'
import GameHistoryPage from '../../src/pages/GameHistoryPage'
import { usePastGames } from '../../src/hooks/usePastGames'
import { mapPastGameToCard } from '../../src/utils/mapGameToCard'
import { AGGREGATE, AVAILABLE, gameResponse } from '../fixtures/games'

vi.mock('../../src/hooks/usePastGames', () => ({ usePastGames: vi.fn() }))

const final = (overrides) =>
  mapPastGameToCard(
    gameResponse({
      status: 'FINAL',
      awayScore: 7,
      homeScore: 21,
      winnerTeamId: 'home',
      ...overrides,
    }),
  )

function render(games) {
  usePastGames.mockReturnValue({
    data: games,
    isPending: false,
    isError: false,
    isFetching: false,
    refetch: () => {},
  })
  return renderToStaticMarkup(<GameHistoryPage />)
}

beforeEach(() => usePastGames.mockReset())

it('shows completed games with scores in matchup order', () => {
  const html = render([final({ id: 'a' })])
  expect(html).toContain('Texas Longhorns vs Oklahoma Sooners')
  expect(html).toContain('7 – 21')
  expect(html).toContain('Showing all 1 completed games')
})

it('distinguishes no stored prediction from a game awaiting a consensus', () => {
  const html = render([
    final({ id: 'none' }),
    final({ id: 'stored', predictions: AVAILABLE }),
  ])
  expect(html.match(/No pregame prediction/g)).toHaveLength(2)
  expect(html).toContain('Consensus unavailable')
  expect(html).not.toContain('Correct</span>')
  expect(html).not.toContain('Missed</span>')
})

it('shows consensus outcomes and summary figures once aggregates exist', () => {
  const html = render([
    final({ id: 'right', predictions: { ...AVAILABLE, aggregate: AGGREGATE } }),
    final({
      id: 'wrong',
      winnerTeamId: 'away',
      awayScore: 28,
      predictions: { ...AVAILABLE, aggregate: AGGREGATE },
    }),
  ])
  expect(html).toContain('Correct</span>')
  expect(html).toContain('Missed</span>')
  expect(html).toContain('1 of 2')
  expect(html).toContain('50.0%')
})

it('calculates model performance from stored predictions', () => {
  const html = render([final({ id: 'a', predictions: AVAILABLE })])
  // Elo picked the winner; Glicko-2 did not.
  expect(html).toContain('Elo')
  expect(html).toContain('1 of 1 correct')
  expect(html).toContain('0 of 1 correct')
  expect(html).toContain('100.0%')
})

it('shows an empty state when no completed game has a stored prediction', () => {
  const html = render([final({ id: 'a' })])
  expect(html).toContain(
    'No completed games have a stored pregame prediction yet.',
  )
})
