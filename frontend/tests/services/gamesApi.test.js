import { afterEach, expect, it, vi } from 'vitest'
import { getPastGames } from '../../src/services/gamesApi'

afterEach(() => vi.unstubAllGlobals())

it('requests final games for the selected season and forwards cancellation', async () => {
  const fetchMock = vi
    .fn()
    .mockResolvedValue({ ok: true, json: async () => [] })
  vi.stubGlobal('fetch', fetchMock)
  const signal = new AbortController().signal
  await expect(getPastGames({ season: 2026, signal })).resolves.toEqual([])
  expect(fetchMock).toHaveBeenCalledWith(
    '/api/games?status=FINAL&season=2026',
    { signal },
  )
})

it('propagates HTTP errors rather than returning an empty list', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 503 }))
  await expect(getPastGames({ season: 2026 })).rejects.toThrow('503')
})
