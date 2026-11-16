// Circular gauge showing a win probability and the abbreviation of the team it
// belongs to, colored by that team's side.
export default function ConfidenceGauge({ percentage, label = '', side }) {
  // Ring size and how much of it to fill based on the percentage
  const r = 26
  const circ = 2 * Math.PI * r
  const offset = circ - (percentage / 100) * circ

  return (
    <span
      className={`confidence-gauge team-${side}`}
      role="img"
      aria-label={`${percentage}% win probability for ${label}`}
    >
      <svg width="64" height="64" viewBox="0 0 64 64" aria-hidden="true">
        <circle className="gauge-track" cx="32" cy="32" r={r} />
        <circle
          className="gauge-fill"
          cx="32"
          cy="32"
          r={r}
          strokeDasharray={circ}
          strokeDashoffset={offset}
        />
      </svg>
      <span className="gauge-text" aria-hidden="true">
        <span className="gauge-percentage">{percentage}%</span>
        <span className="gauge-label">{label}</span>
      </span>
    </span>
  )
}
