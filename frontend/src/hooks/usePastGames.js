import { useQuery } from '@tanstack/react-query'
import { getPastGames } from '../services/gamesApi'
import { mapPastGameToCard } from '../utils/mapGameToCard'

// Sort a copy so cached API responses retain their original order.
const selectPastGameCards = (games) =>
  [...games]
    .sort((a, b) => new Date(b.scheduledAt) - new Date(a.scheduledAt))
    .map(mapPastGameToCard)

/** Shares current-season final results separately from upcoming games. */
export function usePastGames(season) {
  return useQuery({
    queryKey: ['games', { status: 'FINAL', season }],
    queryFn: ({ signal }) => getPastGames({ season, signal }),
    select: selectPastGameCards,
    staleTime: 5 * 60 * 1000,
    gcTime: 30 * 60 * 1000,
    retry: 1,
    refetchOnMount: true,
    refetchOnWindowFocus: true,
    refetchOnReconnect: true,
  })
}
