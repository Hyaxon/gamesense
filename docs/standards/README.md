# Standards & Guidelines

This directory holds our team's coding conventions, naming rules, formatting standards, and testing practices across Python, JavaScript, and Java.

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
- **Files:** React/Vue components use `PascalCase.tsx` (e.g., `UserProfile.tsx`). Utilities, hooks, and configs use `camelCase.js` or `kebab-case.js`.
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