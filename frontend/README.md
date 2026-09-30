# GameSense Frontend

The React frontend for GameSense, built with Vite. The initial application displays a GameSense heading. Routing, application pages, and backend integration will be added in later work.

## Requirements

- Node.js 22.12 or newer, or Node.js 20.19+ within version 20
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

| Command | Purpose |
| --- | --- |
| `npm ci` | Install the exact locked dependencies for a clean installation. |
| `npm run build` | Create the production build in `dist/`. |
| `npm run preview` | Serve the production build locally after building. |
| `npm run lint` | Run the currently configured Oxlint checks. |

Formatting and linting standards are being handled in a separate PR. No test runner is configured yet.

## Project structure

```text
frontend/
├── src/
│   ├── components/  # Reusable UI components
│   ├── pages/       # Application pages
│   ├── services/    # Future API and service code
│   ├── utils/       # Shared helper functions
│   ├── App.jsx      # Root React component
│   ├── index.css    # Global styles
│   └── main.jsx     # React entry point
├── tests/           # Future frontend tests
├── index.html       # HTML entry point and browser title
├── package.json     # Dependencies and scripts
├── package-lock.json
└── vite.config.js   # Vite configuration
```

Empty folders contain `.gitkeep` files so Git preserves them. Remove each placeholder when adding files to that folder.
