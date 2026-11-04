function mapTeam(team, teamId) {
  return {
    id: teamId,
    name: team?.name ?? 'Unknown team',
    logo: team?.shortName ?? '—',
    conference: team?.conference,
  }
}

export function mapGameToCard(game) {
  const scheduledAt = new Date(game.scheduledAt)

  const date = scheduledAt.toLocaleDateString('en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  })

  const time = scheduledAt.toLocaleTimeString('en-US', {
    hour: 'numeric',
    minute: '2-digit',
    timeZoneName: 'short',
  })

  return {
    id: game.id,
    dateTime: `${date} • ${time}`,
    awayTeam: mapTeam(game.awayTeam, game.awayTeamId),
    homeTeam: mapTeam(game.homeTeam, game.homeTeamId),
  }
}