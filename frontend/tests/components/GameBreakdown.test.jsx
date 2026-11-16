import { renderToStaticMarkup } from 'react-dom/server'
import { expect, it } from 'vitest'
import GameBreakdown from '../../src/components/GameBreakdown'
import { mapGameToCard } from '../../src/utils/mapGameToCard'
import { AGGREGATE, AVAILABLE, gameResponse } from '../fixtures/games'

const render = (game) =>
  renderToStaticMarkup(<GameBreakdown game={mapGameToCard(game)} />)

it('lists every returned model with its win chance and no accuracy badge', () => {
  const html = render(gameResponse({ predictions: AVAILABLE }))
  expect(html).toContain('Texas Longhorns @ Oklahoma Sooners')
  expect(html).toContain('Elo')
  expect(html).toContain('71% OU')
  expect(html).toContain('Glicko-2')
  expect(html).toContain('55% UT')
  expect(html.match(/class="prediction-row"/g)).toHaveLength(2)
  expect(html).not.toMatch(/High|Medium|Low/)
})

it('shows "Consensus unavailable" until an aggregate is returned', () => {
  const html = render(gameResponse({ predictions: AVAILABLE }))
  expect(html).toContain('Consensus unavailable')
  // The old hardcoded consensus value must not return.
  expect(html).not.toContain('67%')
})

it('shows the consensus pick when an aggregate is returned', () => {
  const html = render(
    gameResponse({ predictions: { ...AVAILABLE, aggregate: AGGREGATE } }),
  )
  expect(html).toContain('67% Oklahoma Sooners')
  expect(html).not.toContain('Consensus unavailable')
})

it.each([
  [{ state: 'PENDING', models: [] }, 'Predictions are being calculated'],
  [{ state: 'UNAVAILABLE', models: [] }, 'Predictions are unavailable'],
  [undefined, 'Predictions are unavailable'],
])('explains missing model results (%#)', (predictions, message) => {
  const html = render(gameResponse({ predictions }))
  expect(html).toContain(message)
  expect(html).not.toContain('prediction-row')
})

it('omits kickoff time and venue the data does not provide', () => {
  const html = render(gameResponse())
  expect(html).toContain('Sat, Sep 12')
  expect(html).not.toContain('Kickoff')
  expect(html).not.toContain('breakdown-venue')
})

it('prompts for a selection when no game is selected', () => {
  expect(renderToStaticMarkup(<GameBreakdown game={undefined} />)).toContain(
    'Select a matchup',
  )
})
