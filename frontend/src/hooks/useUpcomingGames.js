import { useQuery } from '@tanstack/react-query'
import { getUpcomingGames } from '../services/gamesApi'
import { mapGameToCard } from '../utils/mapGameToCard'

const selectGameCards = (games) => games.map(mapGameToCard)

export function useUpcomingGames() {
  return useQuery({
    queryKey: ['games', { status: 'SCHEDULED' }],
    queryFn: ({ signal }) => getUpcomingGames({ signal }),
    select: selectGameCards,
    staleTime: 5 * 60 * 1000,
    gcTime: 30 * 60 * 1000,
    retry: 1,
    refetchOnMount: true,
    refetchOnWindowFocus: true,
    refetchOnReconnect: true,
  })
}