import './PredictorPage.css'

export default function PredictorPage() {
  return (
    <div className="predictor-page">
      <header className="predictor-page-header">
        <h1>Custom Predictor</h1>
        <p className="predictor-intro">
          Build any matchup. Compare the models. Find your edge.
        </p>
      </header>

      <div className="predictor-grid">
        <section
          className="predictor-region matchup-region"
          aria-labelledby="matchup-title"
        >
          <div className="predictor-section-heading">
            <div>
              <h2 id="matchup-title">1. Build your matchup</h2>
              <p className="region-description">
                Choose two teams and the conditions for your prediction.
              </p>
            </div>
            <span className="section-index">01</span>
          </div>

          <div className="matchup-selectors">
            <label className="team-selector">
              <span className="field-label">Away team</span>
              <select defaultValue="">
                <option value="" disabled>
                  Select a team
                </option>
                {/* INSERT CODE HERE LATER */}
              </select>
            </label>

            <span className="matchup-divider" aria-hidden="true">
              VS
            </span>

            <label className="team-selector">
              <span className="field-label">Home team</span>
              <select defaultValue="">
                <option value="" disabled>
                  Select a team
                </option>
                {/* INSERT CODE HERE LATER */}
              </select>
            </label>
          </div>
        </section>

        <section
          className="predictor-region results-region"
          aria-labelledby="results-title"
        >
          <div className="predictor-section-heading">
            <div>
              <h2 id="results-title">Your Prediction</h2>
              <p className="region-description">
                [A] vs [B] * [Year] season * [Venue]
              </p>
            </div>
          </div>
          <div className="prediction-empty-state">
            <div className="prediction-empty-icon" aria-hidden="true">
              ↗
            </div>
            <h3>Your prediction will appear here</h3>
            <p>Choose a matchup and configure your models to get started.</p>
            {/* INSERT CODE HERE LATER */}
          </div>
        </section>

        <section
          className="predictor-region models-region"
          aria-labelledby="models-title"
        >
          <div className="predictor-section-heading">
            <div>
              <h2 id="models-title">Prediction models</h2>
            </div>
          </div>
          <p className="region-description">
            Choose the models to include in your prediction.
          </p>
          <div className="model-options">{/* INSERT CODE HERE LATER */}</div>
        </section>
      </div>
    </div>
  )
}
