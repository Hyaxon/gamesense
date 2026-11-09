// Embedded teams are optional; keep their canonical IDs and supply display fallbacks.
function mapTeam(team, teamId) {
  const name = team?.name?.trim() || 'Unknown team'
  // Derived initials are a visual placeholder, not an official team abbreviation.
  const initials = team?.name?.trim()
    ? name
        .split(/\s+/)
        .slice(0, 3)
        .map((word) => word[0])
        .join('')
        .toUpperCase()
    : '?'

  return {
    id: teamId,
    name,
    // GameCard currently renders a text badge, not an image URL.
    logo: team?.shortName?.trim() || initials,
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
    isNeutralSite: game.isNeutralSite === true,
    // Preserve the separator GameCard uses to render the date and time separately.
    dateTime: `${date} • ${time}`,
    awayTeam: mapTeam(game.awayTeam, game.awayTeamId),
    homeTeam: mapTeam(game.homeTeam, game.homeTeamId),
  }
}

/** Adds final scores and outcomes without creating prediction data. */
export function mapPastGameToCard(game) {
  const card = mapGameToCard(game)
  const hasScores = game.awayScore != null && game.homeScore != null
  const isTie = hasScores && game.awayScore === game.homeScore
  const hasWinner = [game.awayTeamId, game.homeTeamId].includes(
    game.winnerTeamId,
  )
  const outcome = (teamId) => {
    if (hasWinner) return game.winnerTeamId === teamId ? 'W' : 'L'
    if (isTie) return 'T'
    return null
  }

  return {
    ...card,
    isFinal: true,
    awayTeam: {
      ...card.awayTeam,
      score: game.awayScore,
      outcome: outcome(game.awayTeamId),
    },
    homeTeam: {
      ...card.homeTeam,
      score: game.homeScore,
      outcome: outcome(game.homeTeamId),
    },
  }
}
