import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'

import PredictionMethods from '../../src/components/PredictionMethods.jsx'
import {
  MOCK_AGGREGATE,
  MOCK_PREDICTIONS,
  MOCK_TEAM_A,
  MOCK_TIERS,
} from '../../src/mock/predictionMethodsMock.js'

function render(props) {
  return renderToStaticMarkup(
    <PredictionMethods teamA={MOCK_TEAM_A} {...props} />,
  )
}

const READY = {
  status: 'ready',
  predictions: MOCK_PREDICTIONS,
  aggregate: MOCK_AGGREGATE,
  tiers: MOCK_TIERS,
}

describe('PredictionMethods', () => {
  it('lists the consensus first, then every model, from Team A side', () => {
    const html = render(READY)

    expect(html.indexOf('Consensus Model')).toBeLessThan(
      html.indexOf('Bradley-Terry'),
    )
    expect(html.match(/class="prediction-row/g)).toHaveLength(7)
    expect(html).toContain('Michigan win chance')
    // Glicko-2 favors Team B at 55%, so Team A is at 45%.
    expect(html).toContain('45% Michigan')
    expect(html).toContain('Probabilities are estimates, not guarantees.')
  })

  it('shows a badge per tier and none when the tier is missing', () => {
    const html = render(READY)

    expect(html).toContain('tier-high')
    expect(html).toContain('tier-medium')
    expect(html).toContain('tier-low')
    // 7 rows, TrueSkill has no tier.
    expect(html.match(/class="tier-badge/g)).toHaveLength(6)
  })

  it('shows loading, error and empty states instead of rows', () => {
    expect(render({ status: 'loading' })).toContain('Generating predictions')
    expect(render({ status: 'error' })).toContain('could not be generated')
    expect(render({ status: 'empty' })).toContain('Generate predictions')
    expect(render({ status: 'loading' })).not.toContain('prediction-row')
  })
})
