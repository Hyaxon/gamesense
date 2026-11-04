export async function getUpcomingGames({ signal } = {}) {
  const response = await fetch('/api/games?status=SCHEDULED', {
    signal,
  })

  if (!response.ok) {
    throw new Error(`Unable to load upcoming games (${response.status})`)
  }

  return response.json()
}