import { mapPredictions } from './predictions'

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
    // Cards render a text badge until logo images exist.
    logo: team?.shortName?.trim() || initials,
    conference: team?.conference,
  }
}

// Same conference for both teams names that conference; otherwise the game is
// nonconference. Unknown conferences are left out.
function conferenceLabel(awayTeam, homeTeam) {
  if (!awayTeam.conference || !homeTeam.conference) return null
  return awayTeam.conference === homeTeam.conference
    ? awayTeam.conference
    : 'Nonconference'
}

function formatVenue(venue) {
  if (!venue?.name) return null
  const place = [venue.city, venue.state].filter(Boolean).join(', ')
  return place ? `${venue.name} · ${place}` : venue.name
}

/**
 * Converts a GameResponse into the fields used by the Games and Game History
 * pages. Assumes the required scheduledAt timestamp follows the API contract;
 * this is a presentation mapper, not a schema validator. Midnight UTC
 * represents a date-only feed value; preserve its UTC date and do not invent a
 * kickoff time. Rankings and records remain absent because the API omits them.
 */
export function mapGameToCard(game) {
  const scheduledAt = new Date(game.scheduledAt)
  // The current API cannot distinguish a real midnight kickoff from date-only
  // ingestion. Treat exact midnight UTC as unknown until precision is explicit.
  const isDateOnly =
    scheduledAt.getUTCHours() === 0 &&
    scheduledAt.getUTCMinutes() === 0 &&
    scheduledAt.getUTCSeconds() === 0 &&
    scheduledAt.getUTCMilliseconds() === 0
  // Known kickoff times display in Eastern Time, as in the reference design.
  const timeZone = isDateOnly ? 'UTC' : 'America/New_York'

  const date = scheduledAt.toLocaleDateString('en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    timeZone,
  })
  const time = isDateOnly
    ? null
    : `${scheduledAt.toLocaleTimeString('en-US', {
        hour: 'numeric',
        minute: '2-digit',
        timeZone,
      })} ET`

  const awayTeam = mapTeam(game.awayTeam, game.awayTeamId)
  const homeTeam = mapTeam(game.homeTeam, game.homeTeamId)

  return {
    id: game.id,
    isNeutralSite: game.isNeutralSite === true,
    date,
    time,
    week: game.week ?? null,
    venue: formatVenue(game.venue),
    conferenceLabel: conferenceLabel(awayTeam, homeTeam),
    awayTeam,
    homeTeam,
    predictions: mapPredictions(game.predictions, homeTeam, awayTeam),
  }
}

/** Adds final scores, the winner, and outcomes without creating prediction data. */
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
    winnerTeamId: hasWinner ? game.winnerTeamId : null,
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
