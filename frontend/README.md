# GameSense Frontend

The React frontend for GameSense, built with Vite. The shared application shell provides branding, responsive navigation, and a consistent content container. Application routes currently render a blank canvas; page content and backend integration will be added in later work.

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
| `npm run build`        | Create the production build in `dist/`.                         |
| `npm run preview`      | Serve the production build locally after building.              |
| `npm run lint`         | Run ESLint on JavaScript and JSX, including tests.              |
| `npm run lint:fix`     | Apply automatic ESLint fixes.                                   |
| `npm run format:check` | Check frontend formatting with Prettier.                        |
| `npm run format`       | Apply Prettier formatting.                                      |

ESLint is configured in `eslint.config.js`; Prettier uses the repository's `.prettierrc.json`. See [standards](../docs/standards/README.md) for the shared conventions. No test runner is configured yet; future tests belong in `tests/`, mirroring `src/`.

## Shared layout and routes

`App.jsx` defines nested React Router routes beneath `MainLayout`. The layout renders `TopNavigation` and a main content container with an `Outlet` for the current page. The root URL redirects to `/games`. All routes currently leave the main content area blank, including unknown URLs.

| URL             | Navigation label                |
| --------------- | ------------------------------- |
| `/games`        | Games                           |
| `/teams`        | Teams                           |
| `/predictions`  | Custom Predictor                |
| `/game-history` | Game History                    |
| `/standings`    | Standings                       |
| `/settings`     | Settings (gear icon on desktop) |

The main navigation labels and paths are defined in `src/routes.js`. To implement a page, add its route beneath `MainLayout` in `App.jsx` with the page component as its element, replacing the corresponding blank route. The shared container supplies the outer spacing: responsive side gutters from 16px to 44px, top and bottom padding from 24px to 40px, and a maximum width of 1640px. It fills the available height below the header. Future pages control their own content and surfaces within these boundaries.

At widths of 1088px and below, the navigation collapses behind a Menu button. Selecting a link closes the menu. Escape closes it and returns focus to the button. Active links have a visible indicator and `aria-current="page"`. Keyboard users can also use the skip-to-content link.

For a manual layout check:

1. Run `npm run dev` and visit the local URL.
2. Navigate to every available route and confirm the header and active indicator while the main content remains blank.
3. Reload a route directly and use browser Back/Forward to verify navigation.
4. Resize to desktop, tablet, and phone widths, including 320px. Confirm the Menu button reveals all links and the page has no horizontal overflow.
5. Use Tab to reach the skip link and navigation. Verify visible focus and Escape behavior in the open mobile menu.

Production hosting must serve `index.html` for application routes so direct visits and reloads work with `BrowserRouter`.

## Project structure

```text
frontend/
├── src/
│   ├── components/  # MainLayout and TopNavigation
│   ├── pages/       # Future application pages
│   ├── services/    # Future API and service code
│   ├── utils/       # Shared helper functions
│   ├── App.jsx      # Root React component
│   ├── index.css    # Global styles
│   ├── main.jsx     # React entry point
│   └── routes.js    # Main navigation paths and labels
├── tests/           # Future frontend tests
├── index.html       # HTML entry point and browser title
├── package.json     # Dependencies and scripts
├── package-lock.json
└── vite.config.js   # Vite configuration
```

Empty folders contain `.gitkeep` files so Git preserves them. Remove each placeholder when adding files to that folder.
