// Embedded teams are optional; keep their canonical IDs and supply display fallbacks.
function mapTeam(team, teamId) {
  return {
    id: teamId,
    name: team?.name ?? 'Unknown team',
    // GameCard currently renders a text badge, not an image URL.
    logo: team?.shortName ?? '—',
    conference: team?.conference,
  }
}

/**
 * Converts a GameResponse into the fields consumed by GameCard.
 * Assumes the required scheduledAt timestamp follows the API contract; this is
 * a presentation mapper, not a schema validator. Formats in the viewer's timezone.
 * Rankings, records, and predictions remain absent because the API omits them.
 */
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
    // Preserve the separator GameCard uses to render the date and time separately.
    dateTime: `${date} • ${time}`,
    awayTeam: mapTeam(game.awayTeam, game.awayTeamId),
    homeTeam: mapTeam(game.homeTeam, game.homeTeamId),
  }
}
