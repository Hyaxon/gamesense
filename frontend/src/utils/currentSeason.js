/** Matches GameMapper's July boundary, including January postseason games. */
export function getCurrentSeason(date = new Date()) {
  return date.getUTCMonth() >= 6
    ? date.getUTCFullYear()
    : date.getUTCFullYear() - 1
}
