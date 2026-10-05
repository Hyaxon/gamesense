# Standards & Guidelines

This directory holds our team's coding conventions, naming rules, formatting standards, and testing practices across Python, JavaScript, and Java.

## Enforced tooling

| Language | Linting | Formatting | Configuration |
| --- | --- | --- | --- |
| Python | Ruff: errors, unused code, imports, bug patterns, modern syntax, naming | Ruff: 88 columns, four spaces, double quotes | `prediction/pyproject.toml` |
| Java | Checkstyle: naming, imports, whitespace, common mistakes | Spotless with pinned Google Java Format: two spaces | `backend/pom.xml`, `config/checkstyle/checkstyle.xml` |
| JavaScript / JSX | ESLint recommended rules, React Hooks, JSX variable usage, React Refresh | Prettier: 80 columns, two spaces, single quotes, no semicolons | `frontend/eslint.config.js`, `.prettierrc.json` |

ESLint stays on major version 9 to satisfy `eslint-plugin-react`'s supported peer range; upgrade them together when the plugin supports ESLint 10.

Source files and tests follow the same checks. Generated output, dependencies, and caches are excluded. Prettier runs over frontend JavaScript, JSX, CSS, HTML, JSON, and Markdown; the generated npm lockfile is excluded. `.editorconfig` supplies shared UTF-8, LF, indentation, and final-newline defaults. Formatters own layout; naming and design conventions below that tools cannot infer remain review responsibilities.

See [Contributing](../../CONTRIBUTING.md#local-quality-checks) for validation and automatic-fix commands. Java `verify` runs tests, Checkstyle, and Spotless; CI uses the same configurations and never rewrites files.

## Markdown standards

Repository-wide Markdown checks use `.markdownlint-cli2.jsonc` and markdownlint-cli2. Keep one space after heading markers and blank lines around headings, lists, and fenced code blocks. Avoid tabs, extra trailing whitespace, and repeated blank lines; end files with a newline. Embedded HTML and long lines are allowed. See [Contributing](../../CONTRIBUTING.md#markdown-checks) for check and fix commands.

## Source and test layout

Keep tests inside the service they exercise, separate from production source:

- **Python:** application modules in `prediction/src/gamesense_prediction/`; tests in `prediction/tests/`, named `test_<module>.py` (endpoint tests may name the behavior, such as `test_health.py`). Import `gamesense_prediction`, never `src`; install the package with `python -m pip install -e ".[dev]"` from `prediction/` first. Pytest discovers `tests/` and uses importlib mode.
- **Java:** application classes in `backend/src/main/java/`; JUnit tests in `backend/src/test/java/`, mirroring the source package and using `<ClassName>Test.java`.
- **Frontend:** React code and JavaScript helpers in `frontend/src/`; Vitest tests in `frontend/tests/`, mirroring source subdirectories and using `<module>.test.js` or `<Component>.test.jsx`. Run `npm test` from `frontend/` for a single pass, or `npm run test:watch` during development. Vitest shares `frontend/vite.config.js`, discovers tests under `tests/`, and uses the Node.js environment with explicitly imported test APIs. ESLint and Prettier include source and tests; CI runs all three checks. Keep JavaScript dependencies in the frontend's `package.json` and `package-lock.json`.

Tool references: [Ruff configuration](https://docs.astral.sh/ruff/configuration/), [ESLint configuration](https://eslint.org/docs/latest/use/configure/configuration-files), [Spotless Maven](https://github.com/diffplug/spotless/tree/main/plugin-maven).

## General Principles

- **No generic names:** Avoid placeholder names like `data`, `info`, `temp`, `obj`, `thing`, `utils2.py`, `temp.js`, `stuff.ts`, or `data1` unless scoped locally.
- **No unapproved abbreviations:** Use full words (e.g., `manager`, not `mgr`; `user`, not `usr`).
- **Single Responsibility:** Strive for one file, one responsibility.
- **Test File Mirroring:** Test files must mirror source file names (e.g., `userService.js` -> `userService.test.js`).
- **Booleans:** Universal prefix rule (`is`, `has`, `can`, `should`) across all languages.

---

## Python Standards

- **Files/Modules:** `snake_case.py` (all lowercase, no hyphens).
- **File Headers:** Include a header at the top of Python files summarizing the contained classes.
- **Packages/Folders:** Short, lowercase, no underscores if possible.
- **Variables & Functions:** `snake_case` (functions should be verb-first, e.g., `get_user()`, `calculate_total()`).
- **Constants:** `CAPITAL_SNAKE_CASE` (e.g., `MAX_RETRIES = 5`).
- **Booleans:** Prefix with `is`, `has`, `can`, `should_` (e.g., `is_active`, `has_permission`).
- **Classes & Exceptions:** `PascalCase` (exceptions must end with `Error` or `Exception`).
- **Private Members:** Use a single underscore (`_internal_method`) for internal convention, and double underscores (`__truly_private`) for name-mangling.

---

## JavaScript / TypeScript Standards

- **Files:** React components use `PascalCase.jsx` (or `PascalCase.tsx` if TypeScript is introduced) (e.g., `UserProfile.tsx`). Utilities, hooks, and configs use `camelCase.js` or `kebab-case.js`.
- **Folders:** Use `kebab-case` or `camelCase`, matching the chosen file convention (e.g., `user-profile/`).
- **Hooks:** Must start with `use` (e.g., `useAuth.ts`, `useFetch.ts`).
- **Variables & Functions:** `camelCase` (e.g., `userCount`, `getUser()`).
- **Constants:** `SCREAMING_SNAKE_CASE` for module-level constants.
- **Booleans:** Prefix with `is`, `has`, `should`, `can` (e.g., `isLoading`, `hasError`).
- **Event Handlers:** Use `handle` prefix internally (`handleClick`), and `on` prefix for props (`onClick`).
- **Types & Interfaces:** `PascalCase` (e.g., `type OrderStatus`, `interface UserPayload`).
- **Private Fields:** Prefer native private fields (`#truePrivate`) over older conventional styles.

---

## Java Standards

- **Files:** `PascalCase.java`, must match the public class name exactly.
- **Packages:** All lowercase, reverse domain style, no underscores (e.g., `com.company.userservice`).
- **Variables, Fields & Methods:** `camelCase` (methods must be verb-first).
- **Constants:** `CAPITAL_SNAKE_CASE`, declared `static final`.
- **Classes, Interfaces & Enums:** `PascalCase` (interfaces do not use an `I` prefix).
- **Generics:** Use single uppercase letters with conventional meanings (`T = Type`, `E = Element`, `K = Key`, `V = Value`, `N = Number`).
- **Private Fields:** No underscore prefixes; rely strictly on the `private` keyword (e.g., `private String userName;`, never `_userName`).
- **Getters & Setters:** Strict `get`, `set`, and `is` prefixes (use `isActive()` for booleans, never `getActive()`).
- **Exceptions:** `PascalCase`, ending with `Exception`.
- **Annotations:** `PascalCase` with no special prefix (e.g., `@Override`, `@LogExecutionTime`).
