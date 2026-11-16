/** One model's prediction in the Game Breakdown: name, description, and win chance.
 * The accuracy column stays empty until evaluated model accuracy exists (#121). */
export default function PredictionRow({ model }) {
  return (
    <li className="prediction-row">
      <span className="model-icon" aria-hidden="true">
        {model.icon}
      </span>
      <span className="model-text">
        <span className="model-title-row">
          <span className="model-name">{model.name}</span>
          <span
            className="info-icon"
            title={`${model.name}: ${model.description}`}
            aria-hidden="true"
          >
            ⓘ
          </span>
        </span>
        <span className="model-desc">{model.description}</span>
      </span>
      <span className={`prediction-value team-${model.side}`}>
        {model.percentage}% {model.abbreviation}
        <span className="visually-hidden">
          {' '}
          win chance for {model.teamName}
        </span>
      </span>
      {/* PLACEHOLDER: accuracy badge (#121) */}
      <span className="accuracy-tier" />
    </li>
  )
}
