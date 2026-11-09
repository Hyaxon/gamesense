/**
 * Fetches scheduled GameResponse objects from the Java API without mapping them.
 * Accepts the query hook's AbortSignal so canceled requests stop fetching.
 * HTTP, network, and JSON parsing errors propagate to the caller.
 */
export async function getUpcomingGames({ signal } = {}) {
  const response = await fetch('/api/games?status=SCHEDULED', {
    signal,
  })

  // Fetch resolves for HTTP errors; throw so the query exposes an error state.
  if (!response.ok) {
    throw new Error(`Unable to load upcoming games (${response.status})`)
  }

  return response.json()
}

/** Retrieves completed games for one season; failures remain query errors. */
export async function getPastGames({ season, signal }) {
  const params = new URLSearchParams({
    status: 'FINAL',
    season: String(season),
  })
  const response = await fetch(`/api/games?${params}`, { signal })

  if (!response.ok) {
    throw new Error(`Unable to load past games (${response.status})`)
  }

  return response.json()
}
