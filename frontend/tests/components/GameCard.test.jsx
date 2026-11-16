import { renderToStaticMarkup } from 'react-dom/server'
import { expect, it } from 'vitest'
import GameCard from '../../src/components/GameCard'
import { mapGameToCard } from '../../src/utils/mapGameToCard'
import { AGGREGATE, AVAILABLE, gameResponse } from '../fixtures/games'

const render = (game, isSelected = false) =>
  renderToStaticMarkup(
    <GameCard
      game={mapGameToCard(game)}
      isSelected={isSelected}
      onSelect={() => {}}
    />,
  )

it('shows the matchup, conference lines, and date', () => {
  const html = render(gameResponse())
  expect(html).toContain('Texas Longhorns @ Oklahoma Sooners')
  expect(html).toContain('Southeastern Conference - Texas Longhorns')
  expect(html).toContain('Southeastern Conference - Oklahoma Sooners')
  expect(html).toContain('Sat, Sep 12')
  expect(html).toContain('View game →')
})

it.each([
  [undefined, 'Predictions unavailable'],
  [{ state: 'PENDING', models: [] }, 'Calculating predictions'],
  [{ state: 'UNAVAILABLE', models: [] }, 'Predictions unavailable'],
  [AVAILABLE, 'Consensus unavailable'],
])(
  'fills the consensus slot without a consensus (%#)',
  (predictions, label) => {
    const html = render(gameResponse({ predictions }))
    expect(html).toContain(label)
    expect(html).not.toContain('<svg')
    expect(html).not.toContain('%')
  },
)

it('shows the consensus ring for the favored team when an aggregate exists', () => {
  const html = render(
    gameResponse({ predictions: { ...AVAILABLE, aggregate: AGGREGATE } }),
  )
  expect(html).toContain('<svg')
  expect(html).toContain('67%')
  expect(html).toContain('team-home')
  expect(html).not.toContain('Consensus unavailable')
})

it.each([false, true])(
  'exposes native button semantics and selected=%s',
  (isSelected) => {
    const html = render(gameResponse(), isSelected)
    expect(html).toMatch(/^<button /)
    expect(html).toContain('type="button"')
    expect(html).toContain(`aria-pressed="${isSelected}"`)
    expect(html).toContain(isSelected ? 'Selected' : 'View game →')
    expect(html).not.toContain('<div')
  },
)
