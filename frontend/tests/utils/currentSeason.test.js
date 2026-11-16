import { expect, it } from 'vitest'
import { getCurrentSeason } from '../../src/utils/currentSeason'

it.each([
  ['2026-01-01T12:00:00Z', 2025],
  ['2026-06-30T23:59:59Z', 2025],
  ['2026-07-01T00:00:00Z', 2026],
  ['2026-11-08T00:00:00Z', 2026],
])('maps %s to football season %s', (timestamp, season) => {
  expect(getCurrentSeason(new Date(timestamp))).toBe(season)
})
