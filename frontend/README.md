# GameSense Frontend

The React frontend for GameSense, built with Vite. The shared application shell provides branding, responsive navigation, and a consistent content container. The Games page lists upcoming games beside a Game Breakdown for the selected game, and Game History compares completed games with their stored predictions. Both pages render from the Java Games API, including its embedded predictions. Other application routes render a blank canvas.

## Requirements

- Node.js 22.12 or newer (Node.js 24 is used in CI)
- npm

## Local development

From the repository root:

```bash
cd frontend
npm install
npm run dev
```

Open the local URL printed in the terminal (usually `http://localhost:5173`). Saving source files updates the page automatically. Press `Ctrl+C` to stop the server.

Commit `package.json` and `package-lock.json` when dependencies change. Do not commit `node_modules/` or `dist/`.

## Build and checks

Run these commands from `frontend/`:

| Command                | Purpose                                                         |
| ---------------------- | --------------------------------------------------------------- |
| `npm ci`               | Install the exact locked dependencies for a clean installation. |
| `npm test`             | Run the frontend JavaScript unit tests once with Vitest.        |
| `npm run test:watch`   | Rerun unit tests as files change during development.            |
| `npm run build`        | Create the production build in `dist/`.                         |
| `npm run preview`      | Serve the production build locally after building.              |
| `npm run lint`         | Run ESLint on JavaScript and JSX, including tests.              |
| `npm run lint:fix`     | Apply automatic ESLint fixes.                                   |
| `npm run format:check` | Check frontend formatting with Prettier.                        |
| `npm run format`       | Apply Prettier formatting.                                      |

ESLint is configured in `eslint.config.js`; Prettier uses the repository's `.prettierrc.json`. See [standards](../docs/standards/README.md) for the shared conventions.

## Unit tests

Vitest shares the frontend's Vite configuration in `vite.config.js` and discovers `tests/**/*.test.js` and `tests/**/*.test.jsx`. Tests run in Node.js and explicitly import test APIs from `vitest`. Keep tests in `tests/`, mirroring the subdirectories in `src/`. Run `npm test` for a single pass or `npm run test:watch` while developing. CI runs the suite alongside linting, formatting, and the production build.

The prototype prediction validator lives in `src/utils/predictionValidation.js`, with its tests in `tests/utils/predictionValidation.test.js`. It checks the earlier `method`, `predictedWinner`, and `confidenceScore` fields; it does not validate the canonical [prediction model interface](../docs/architecture/prediction-model-interface.md). JavaScript dependencies and tests are managed entirely by the frontend npm package.

## Shared layout and routes

`App.jsx` defines nested React Router routes beneath `MainLayout`. The layout renders `TopNavigation` and a main content container with an `Outlet` for the current page. The root URL redirects to `/games`, which renders `GamesPage`. Game History renders current-season final results at `/game-history`. Other routes currently leave the main content area blank, including unknown URLs.

| URL             | Navigation label                |
| --------------- | ------------------------------- |
| `/games`        | Games                           |
| `/teams`        | Teams                           |
| `/predictions`  | Custom Predictor                |
| `/game-history` | Game History                    |
| `/standings`    | Standings                       |
| `/settings`     | Settings (gear icon on desktop) |

The main navigation labels and paths are defined in `src/routes.js`. To implement a page, add its route beneath `MainLayout` in `App.jsx` with the page component as its element, replacing the corresponding blank route. The shared container supplies the outer spacing: responsive side gutters from 16px to 32px, top and bottom padding from 16px to 24px, and a maximum width of 1640px. It fills the available height below the header. The global `--layout-spacing` variable also controls the gap between Games regions and their inner padding. Future pages control their own content and surfaces within these boundaries.

At widths of 1088px and below, the navigation collapses behind a Menu button. Selecting a link closes the menu. Escape closes it and returns focus to the button. Active links have a visible indicator and `aria-current="page"`. Keyboard users can also use the skip-to-content link.

For a manual layout check:

1. Run `npm run dev` and visit the local URL.
2. Navigate to every available route and confirm the header and active indicator. Games shows its two regions; the other pages remain blank.
3. Reload a route directly and use browser Back/Forward to verify navigation.
4. Resize to desktop, tablet, and phone widths, including 320px. Confirm the Menu button reveals all links and the page has no horizontal overflow.
5. Use Tab to reach the skip link and navigation. Verify visible focus and Escape behavior in the open mobile menu.

Production hosting must serve `index.html` for application routes so direct visits and reloads work with `BrowserRouter`.

## Games page layout

`src/pages/GamesPage.jsx` follows the Games page reference design in #68: a page header, then Upcoming games on the left and the Game Breakdown on the right. The page inherits its outer gutters, vertical spacing, and maximum width from `MainLayout`.

Each GameCard row shows a logo placeholder, the matchup, each team's conference and name, a consensus slot, the date, and "View game →" or "Selected". The list scrolls within its panel so the "How to read predictions" note stays visible, and the Breakdown stays in view while scrolling. The first game is selected until another is chosen.

The Game Breakdown renders the selected game from the list response, without another request: matchup, date, both teams, the consensus box, and one row per model the API returned with its win chance. Model rows come from the API, so models that are not implemented or did not succeed do not appear.

Placeholders follow the design's dependencies on other tickets:

| Element                           | Shown now                                                        | Owned by           |
| --------------------------------- | ---------------------------------------------------------------- | ------------------ |
| Consensus ring and box            | "Consensus unavailable" until the API returns an `aggregate`     | #137               |
| Model accuracy badges             | Empty column                                                     | #121               |
| Rankings and records              | Omitted                                                          | #117               |
| Filters, search, sort, pagination | Omitted                                                          | #23, #24, #25, #26 |
| Team logos and stadium banner     | Text badges and a generic banner                                 | Image sources      |
| Kickoff time, venue, week         | Shown only when the API provides them; the current feed does not | Data source        |

At widths of 1024px and below, the sections stack with Upcoming games first.

To validate, visit `/games` at a desktop width (for example, 1440px) and confirm the two equally sized regions appear side by side. Resize to 1024px, 768px, and 320px and confirm they stack, retain spacing, and stay within the viewport. Reload `/games` directly and verify the Games navigation item remains active.

## Upcoming games API integration

Start Java in a separate terminal from the repository root:

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw spring-boot:run
```

The Java selection command above is for macOS. On other systems, configure
`JAVA_HOME` for JDK 17. Wait for startup ingestion to finish; the current-season
feed requires network access. Python is not required for games. See the
[backend instructions](../backend/README.md#running-the-backend) for details.
Then start Vite using the local development commands above and open `/games`.

Vite forwards `/api` requests unchanged to `http://localhost:8080`. The list calls
`GET /api/games?status=SCHEDULED`; the React page itself uses `/games`. The proxy
is development-only. Production hosting must route `/api` to Java separately
from the frontend's SPA fallback.

### Responsibilities

| File                            | Responsibility                                                                                                                                     |
| ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `src/services/gamesApi.js`      | Fetch scheduled games, pass cancellation through, and throw for failed HTTP responses. Return the original JSON array.                             |
| `src/utils/mapGameToCard.js`    | Map game responses to card fields, preserve date-only UTC dates and format known kickoff times in Eastern Time, and supply missing-team fallbacks. |
| `src/utils/predictions.js`      | Map embedded predictions to display rows and the consensus pick, score History outcomes, and decide when to poll for pending predictions.          |
| `src/hooks/useUpcomingGames.js` | Define the query key, mapping selector, cache policy, retries, and refetch triggers.                                                               |
| `src/main.jsx`                  | Create one QueryClient and provide it above the router.                                                                                            |
| `src/pages/GamesPage.jsx`       | Render initial loading, connection waiting, errors with retry, cached refresh states, and selection.                                               |

The mapper assumes the [GameResponse contract](../docs/architecture/api-schemas.md#gameresponse)
and does not validate arbitrary JSON. Missing embedded teams display as Unknown
team with a question-mark badge. The original 44 × 44 logo placeholder uses
shortName when available, otherwise up to three initials from the full team name.
These initials are display placeholders, not official team abbreviations, and
also label win chances. Rankings, records, and predictions are not fabricated.
Without a consensus, the consensus slot shows Calculating predictions while the
backend is still predicting (`PENDING`), Predictions unavailable when no model
succeeded or the game was never predicted, and Consensus unavailable otherwise.
Predictions color by side (home blue, away orange) until team colors exist. An empty successful
array displays No upcoming games found; request failures are not converted to
empty arrays. Mock games are no longer the list's runtime data source.

### Cache behavior

The query key is `['games', { status: 'SCHEDULED' }]`. Original API responses stay
in the cache; the selector supplies mapped cards to consumers. Successful data is
fresh for five minutes. Stale data can refresh when a consumer mounts, the window
regains focus, or the network reconnects. While any game's predictions are
`PENDING`, the query also polls every five seconds and stops once none are. Unused
entries are retained for thirty minutes after their last consumer unmounts.
Failures retry once automatically, and the page provides a manual retry button.
Cached games remain visible during a refresh and after a refresh failure.

This cache lives in browser memory and resets on a full page reload. Navigating
between React routes preserves it. See the
[query cache decision](../docs/decisions/2026-11-04-frontend-query-cache.md) for
alternatives and tradeoffs. Predictions are calculated by the backend at startup;
the frontend only reads them.

### Manual validation

1. Visit `/api/games?status=SCHEDULED` on the Vite URL and confirm a JSON array.
2. Visit `/games` and compare the rendered teams and dates with that response.
3. Use browser network throttling and reload to observe initial loading.
4. After a successful load, navigate away and back within five minutes. The list
   should reuse fresh data without a new games request.
5. Stop Java and reload the page to test initial failure and retry. Restart Java
   and click Try again to recover.
6. To test an empty store, stop Java and restart from `backend/` with
   `GAMESENSE_INGESTION_ENABLED=false ./mvnw spring-boot:run`. Reload the frontend
   to clear its cache and confirm the empty message. Restart Java normally afterward.
7. For a failed background refresh, load games successfully, stop Java, wait until
   the data is stale, then leave and refocus the browser window. Cached cards should
   remain visible with a refresh error after the retry completes.

## Game history

The Game History navigation item opens `/game-history` and follows the Game
History reference design in #68:

- Summary cards: games reviewed, correct consensus picks, and consensus accuracy.
  The consensus figures show Consensus unavailable until aggregates exist (#137).
- Completed games table: date, matchup with its conference (or Nonconference),
  final score in matchup order, pregame pick, outcome, and a View recap placeholder
  (#73). Games without a stored prediction show No pregame prediction in the pick
  and outcome columns; games with one show Consensus unavailable until an aggregate
  exists, then the pick and a Correct or Missed badge.
- Model performance cards: each model's correct winner picks across completed games
  with a stored prediction from that model, or an empty state when there are none.

Ties and games without a winner are not scored. Missing winners are not assumed.
The backend stores predictions only for games that were scheduled at startup, so
completed games show No pregame prediction until refresh (#65) or persistence (#59).

`getPastGames` calls `/api/games?status=FINAL&season=2026` for season 2026.
`getCurrentSeason` follows GameMapper's July season boundary in UTC, so January
through June belong to the previous year's season. `usePastGames` keys its cache by
status and season, sorts a copy newest-first, and maps through `mapPastGameToCard`.
It uses five-minute freshness, thirty-minute unused retention, one retry, and
stale-data refresh on mount, focus, or reconnect. The page handles loading, empty,
error, and cached-refresh states just like Upcoming Games.

To validate, open Game History and compare it with the filtered API response.
Check newest-first ordering, zero scores, and the pick and outcome columns. Use the
empty-store and backend-unavailable steps above to verify empty and error states.
Automated tests cover season boundaries, service filters and failures, prediction
mapping, consensus and model scoring, and the Games, Breakdown, and History states.

## Project structure

```text
frontend/
├── src/
│   ├── components/  # Layout, navigation, game cards, and breakdown components
│   ├── hooks/       # Cached API queries, including useUpcomingGames
│   ├── pages/       # GamesPage and its page-level styles
│   ├── services/    # HTTP requests to the Java API
│   ├── utils/       # Game-to-card mapping and prediction validation
│   ├── App.jsx      # Root React component
│   ├── index.css    # Global styles
│   ├── main.jsx     # React entry point
│   └── routes.js    # Main navigation paths and labels
├── tests/           # Vitest tests mirroring src/ subdirectories
├── index.html       # HTML entry point and browser title
├── package.json     # Dependencies and scripts
├── package-lock.json
└── vite.config.js   # Vite configuration
```

Empty folders contain `.gitkeep` files so Git preserves them. Remove each placeholder when adding files to that folder.

### Date-only game schedules

The ingestion feed supplies calendar dates without kickoff times. Java stores
these as midnight UTC. Card mapping preserves that UTC calendar date and displays
no time rather than shifting it to the previous local day. Non-midnight
timestamps display in Eastern Time. This applies to both pages. The current contract cannot distinguish a genuine midnight UTC
kickoff from a date-only value; an explicit precision field would be needed to
remove that ambiguity when timed feeds are introduced.
