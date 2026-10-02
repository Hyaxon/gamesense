# Contributing Guidelines

## Branching Strategy
- Create feature branches from `main` using a standard ticket format: `ticket/{ticket-number}-{ticket-name}` (e.g., `ticket/12-setup-docs`).
- Always keep pull requests focused on single tasks or tickets.

## Code Standards

- Create feature branches from `main` using a standard ticket format: `tickets/{ticket-number}-{ticket-name}` (e.g., `tickets/12-setup-docs`).
- Always keep pull requests focused on single tasks or tickets.

## Code Standards

- Follow the language-specific naming conventions and formatting rules outlined in `docs/standards/`.
- Ensure all code compiles and passes local checks before opening a pull request.

## Pull Request Process

1. Push your branch to GitHub.
2. Open a Pull Request targeting the `main` branch.
3. Request a review from a team member before merging.

## Documentation Naming Conventions

- Use **kebab-case** for general documentation files (e.g., `data-pipeline.md`).
- Architectural Decision Records (ADRs) must be prefixed with a date: `YYYY-MM-DD-short-title.md`.

## When to Document What
- **Architecture (`docs/architecture/`):** Use when defining system components, data flows, or API contracts.
- **Standards (`docs/standards/`):** Use when establishing formatting, naming conventions, or testing procedures.
- **Decisions (`docs/decisions/`):** Use when making a significant technical decision (like selecting a library, database, or architectural pattern).

- **Architecture (`docs/architecture/`):** Use when defining system components, data flows, or API contracts.
- **Standards (`docs/standards/`):** Use when establishing formatting, naming conventions, or testing procedures.
- **Decisions (`docs/decisions/`):** Use when making a significant technical decision (like selecting a library, database, or architectural pattern).

## CI checks

GitHub Actions runs on pull requests targeting `main`, new commits to those pull requests, and pushes to `main`. PR branch names must use `tickets/<number>-<description>` with lowercase words separated by hyphens.

The workflow runs Python tests and Ruff checks, Java tests with Checkstyle and Spotless, and frontend ESLint, Prettier, and production build checks. Checks do not automatically fix source files.

The frontend initialization and code-quality configuration tickets must land before all jobs can pass. The code-quality ticket supplies the tool dependencies and configurations, including the frontend `lint` and `format:check` scripts and the Spotless Maven plugin.

Before merging, confirm the workflow passes on GitHub and request a teammate's review. Repository administrators should make the CI checks required in the `main` branch rules if failed checks must block merging.
