import { useQuery } from '@tanstack/react-query'
import { getUpcomingGames } from '../services/gamesApi'
import { mapGameToCard } from '../utils/mapGameToCard'

// A stable selector avoids remapping unchanged data on each render.
// TanStack Query retains the original responses in the shared cache.
const selectGameCards = (games) => games.map(mapGameToCard)

/** Shares scheduled games and request state through the application's query client. */
export function useUpcomingGames() {
  return useQuery({
    // Include server-side criteria so future history queries use a separate entry.
    queryKey: ['games', { status: 'SCHEDULED' }],
    queryFn: ({ signal }) => getUpcomingGames({ signal }),
    select: selectGameCards,
    staleTime: 5 * 60 * 1000, // Reuse successful data as fresh for five minutes.
    gcTime: 30 * 60 * 1000, // Remove data after thirty minutes without consumers.
    retry: 1, // One automatic retry before exposing a failed request.
    // These events refresh stale data; no interval polling is configured.
    refetchOnMount: true,
    refetchOnWindowFocus: true,
    refetchOnReconnect: true,
  })
}
