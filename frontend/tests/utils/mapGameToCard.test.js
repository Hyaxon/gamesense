import { expect, it } from 'vitest'
import { mapGameToCard, mapPastGameToCard } from '../../src/utils/mapGameToCard'

const GAME = { id: 'game', homeTeamId: 'home', awayTeamId: 'away' }

// Run under timezones on both sides of UTC to catch calendar-day shifts.
it.each(['2026-10-31T00:00:00Z', '2026-10-31T00:00:00.000Z'])(
  'preserves the UTC calendar date for date-only %s',
  (scheduledAt) => {
    const game = { ...GAME, scheduledAt }
    expect(mapGameToCard(game).dateTime).toBe(
      'Sat, Oct 31 • Kickoff unavailable',
    )
    expect(mapPastGameToCard(game).dateTime).toBe(
      'Sat, Oct 31 • Kickoff unavailable',
    )
  },
)

it('retains local formatting for known kickoff times', () => {
  const scheduledAt = '2026-10-31T18:30:00Z'
  const timestamp = new Date(scheduledAt)
  const date = timestamp.toLocaleDateString('en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  })
  const time = timestamp.toLocaleTimeString('en-US', {
    hour: 'numeric',
    minute: '2-digit',
    timeZoneName: 'short',
  })
  expect(mapGameToCard({ ...GAME, scheduledAt }).dateTime).toBe(
    `${date} • ${time}`,
  )
})
