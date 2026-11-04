// Circular gauge showing a prediction's confidence percentage and winner
export default function ConfidenceGauge({
  percentage,
  logo = '',
  color = '#3182ce',
}) {
  // Ring size and how much of it to fill based on the percentage
  const r = 26
  const circ = 2 * Math.PI * r
  const offset = circ - (percentage / 100) * circ

  return (
    <div
      style={{
        position: 'relative',
        width: '64px',
        height: '64px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <svg
        width="64"
        height="64"
        viewBox="0 0 64 64"
        style={{ transform: 'rotate(-90deg)' }}
      >
        {/* Background ring */}
        <circle
          cx="32"
          cy="32"
          r={r}
          stroke="#edf2f7"
          strokeWidth="5"
          fill="transparent"
        />
        {/* Progress ring filled to the percentage */}
        <circle
          cx="32"
          cy="32"
          r={r}
          stroke={color}
          strokeWidth="5"
          strokeDasharray={circ}
          strokeDashoffset={offset}
          strokeLinecap="round"
          fill="transparent"
        />
      </svg>
      {/* Percentage and winner label centered in the ring */}
      <div
        style={{ position: 'absolute', textAlign: 'center', lineHeight: '1.1' }}
      >
        <div
          style={{ fontWeight: '800', fontSize: '0.85rem', color: '#1a202c' }}
        >
          {percentage}%
        </div>
        <div
          style={{
            fontWeight: '700',
            fontSize: '0.65rem',
            color: '#718096',
            textTransform: 'uppercase',
          }}
        >
          {logo}
        </div>
      </div>
    </div>
  )
}
