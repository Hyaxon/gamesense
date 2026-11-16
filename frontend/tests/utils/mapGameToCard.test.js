import { expect, it } from 'vitest'
import { mapGameToCard, mapPastGameToCard } from '../../src/utils/mapGameToCard'
import { AWAY, gameResponse } from '../fixtures/games'

const GAME = { id: 'game', homeTeamId: 'home', awayTeamId: 'away' }

// Run under timezones on both sides of UTC to catch calendar-day shifts.
it.each(['2026-10-31T00:00:00Z', '2026-10-31T00:00:00.000Z'])(
  'preserves the UTC calendar date and invents no kickoff for date-only %s',
  (scheduledAt) => {
    const game = { ...GAME, scheduledAt }
    for (const card of [mapGameToCard(game), mapPastGameToCard(game)]) {
      expect(card.date).toBe('Sat, Oct 31')
      expect(card.time).toBeNull()
    }
  },
)

it('shows known kickoff times in Eastern Time', () => {
  // 16:00 UTC is noon Eastern Daylight Time.
  const card = mapGameToCard({ ...GAME, scheduledAt: '2026-09-12T16:00:00Z' })
  expect(card.date).toBe('Sat, Sep 12')
  expect(card.time).toBe('12:00 PM ET')
})

it('labels same-conference and nonconference games', () => {
  expect(mapGameToCard(gameResponse()).conferenceLabel).toBe(
    'Southeastern Conference',
  )
  expect(
    mapGameToCard(
      gameResponse({ awayTeam: { ...AWAY, conference: 'Big 12 Conference' } }),
    ).conferenceLabel,
  ).toBe('Nonconference')
  expect(
    mapGameToCard({ ...GAME, scheduledAt: '2026-10-31T00:00:00Z' })
      .conferenceLabel,
  ).toBeNull()
})

it('formats a venue only when the API provides one', () => {
  expect(mapGameToCard(gameResponse()).venue).toBeNull()
  expect(
    mapGameToCard(
      gameResponse({
        venue: { name: 'Michigan Stadium', city: 'Ann Arbor', state: 'MI' },
      }),
    ).venue,
  ).toBe('Michigan Stadium · Ann Arbor, MI')
})

it('does not fabricate wins or scores when results are unavailable', () => {
  const card = mapPastGameToCard(gameResponse({ status: 'FINAL' }))
  expect(card.winnerTeamId).toBeNull()
  expect(card.awayTeam.outcome).toBeNull()
  expect(card.awayTeam.score).toBeUndefined()
})

it('labels equal final scores as ties without a winner', () => {
  const card = mapPastGameToCard(
    gameResponse({ status: 'FINAL', awayScore: 0, homeScore: 0 }),
  )
  expect(card.awayTeam.outcome).toBe('T')
  expect(card.homeTeam.outcome).toBe('T')
})
