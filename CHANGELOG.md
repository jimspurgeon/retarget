# Changelog

All notable changes to this project are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/); versioning is SemVer-ish
(`vMAJOR.MINOR.PATCH` — see DEVELOPMENT.md).

## [Unreleased]

### Added
- Hand-picked creative packs: `fresh-air` (61 images) and `fruit` (60 images),
  ingested via new `fetch_creatives.py --handpicked` mode. Photos manually
  curated from Unsplash (quality over search-term relevance, imagery-domains.md
  anti-pattern 3), re-encoded to 2160 px long edge @ q85 — visually
  indistinguishable from originals on modern displays.

## [v0.1.0] - 2026-10-02

### Phase 0 — Foundation complete

First tagged snapshot: repo, docs, build, CI, and domain skeletons.

### Added
- AGENTS.md: contributor + AI-agent rules (security, ethics, collaboration, Android
  standards, review checklist).
- `docs/research/` evidence library: advertising psychology, behavior-change
  science, interruption & timing, imagery domains, digital wellbeing guardrails,
  Android platform constraints, BibTeX references.
- DEVELOPMENT.md: product vision, domain model, architecture, phased roadmap,
  branching strategy, definition-of-done, pre-registered decisions.
- Core domain skeletons with unit tests: fatigue-aware `CreativeRotator`,
  `BudgetPolicy` (hard notification caps, quiet hours, dismissal cooldowns),
  `FreshStartCalendar` (temporal landmark boosts).
- CI workflow (build + unit tests), PR template embedding review checklist.
- Gradle build skeleton: wrapper, settings, version catalog, app module with
  Compose shell, Hilt DI wiring, ktlint/detekt config, local.properties template.
- Phase 0 MVP: APK built successfully, 14 tests pass, linters green; core bugs
  fixed (CreativeRotator decay inverted, quiet-hour logic broken).

### Changed
- **App renamed: advert-app → Retarget.** The name reclaims the ad-industry
  practice of following you around the internet and aims it at your own goals.
  Repo renamed to jimspurgeon/retarget (GitHub redirects old URLs). Package
  namespace: com.retarget.*.

### Fixed
- `CreativeRotator.score`: previously scored *never-shown* creatives lowest
  (exponential decay on infinity) and never recovered — fixed to use rest-recovery
  curve (0 → 1 with half-life 72h); cumulative wear penalty added.
- `BudgetPolicy.isQuietHour`: both branches identical, making *every* hour quiet;
  fixed to correctly handle same-day vs. wrapping windows.
- `BudgetPolicy.canDeliver`: self-referencing hardMax (compile error); fixed to
  coerce override ≤ channel-hard-max.
