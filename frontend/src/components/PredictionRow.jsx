/** Reusable row for individual model predictions
 * - isPresage: Flags row as the weighted summary card.
 * - cardColor: Hex color passed down and dynamically applies team color highlighting. (thought i'd add this) */

export default function PredictionRow({
  isPresage,
  icon,
  modelName,
  description,
  formattedPrediction,
  tier,
  cardColor,
}) {
  // Apply dynamic team color border so presage prediction is the color of the predicted winning team
  const presageStyle =
    isPresage && cardColor
      ? {
          borderColor: cardColor,
          backgroundColor: `${cardColor}15`, // opacity
        }
      : {}

  return (
    <div
      className={`prediction-row ${isPresage ? 'presage-card' : ''}`}
      style={presageStyle}
    >
      <div className="model-info-col">
        <span className="model-icon">{icon}</span>
        <div className="model-text">
          <div className="model-title-row">
            <span className="model-name">{modelName}</span>
            <span className="info-icon">ⓘ</span>
          </div>
          <span className="model-desc">{description}</span>
        </div>
      </div>

      <div className="prediction-val-col">
        <span
          className={`prediction-text ${formattedPrediction?.teamClass || ''}`}
        >
          {formattedPrediction?.text}
        </span>
      </div>

      <div className="accuracy-tier-col">
        {tier && (
          <span className={`tier-badge ${tier.className}`}>{tier.label}</span>
        )}
      </div>
    </div>
  )
}
