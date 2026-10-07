# retarget Development Guide

This document is the canonical plan for building retarget. Any agent (AI or human)
picking up work on this repo should read AGENTS.md first, then this file top to bottom.
It is kept in the repo so the project is fully self-contained: the plan, the research,
and the rules all live together.

## Vision

**retarget turns advertising's own toolbox against itself.** Advertisers have spent
decades perfecting how to make things attractive, memorable, and top-of-mind. This app
gives that entire toolkit — imagery, timing, repetition, emotional pairing — to the
user, aimed at their *own* goals: hydration, whole-food eating, time in nature,
breathing, and general wellness.

The pitch in one line: *"You already see dozens of ads a day. Now they're for the life
you actually want."*

Non-goals: habit tracking-for-tracking's-sake, calorie counting, medical claims,
social features, cloud accounts.

## Core Concepts (the domain model)

| Concept | What it is | Ad-world analogy |
|---|---|---|
| **Goal** | User-created aspiration with an identity ("hydrated me") | Brand |
| **Campaign** | Active promotion of a goal over time, with pacing/budget | Ad campaign |
| **Creative** | An image + copy pairing, taggable by sub-theme | Ad creative |
| **Creative Pack** | Curated set of 8-16 creatives for a goal theme | Creative library |
| **Channel** | Delivery surface: wallpaper, notification, overlay, widget | Media placement |
| **Slot** | A candidate delivery window computed by the scheduler | Ad slot / impression opportunity |
| **Exposure Ledger** | On-device record of every impression per creative/channel | Impression log |
| **Budget** | Per-channel daily/weekly caps + cooldowns | Frequency cap |
| **Fresh-start window** | Recognized temporal landmark used for boosts | Seasonal flight |
| **Check-in** | One-tap log that the user did the behavior | Conversion event |

The mental model to keep straight: the **user is the advertiser**, the **goal is the
brand**, and the **user is also the audience**. The scheduler is the media buyer
working for the user's elected goals, within budgets the user controls.

## Architecture

Kotlin, Jetpack Compose, single-module-first (modularize when it hurts). Local-first:
Room database, **no network permission in v1**.

```
app/
  src/main/kotlin/com/retarget/app/
    goal/          # Goal, Campaign domain models + Room entities + DAOs
    creative/      # Creative, CreativePack, licensing metadata, rotation engine
    scheduler/     # Slot computation, budgets, cooldowns, fresh-start boosts
    channels/
      wallpaper/   # WallpaperManager wrapper, revert support, decode pipeline
      notification/# Channels, BigPictureStyle builders, actions
      overlay/     # Full-screen nudge activity (breathing etc.)
      widget/      # Glance widgets (Phase 2)
    analytics/     # LOCAL-ONLY metrics: exposures, check-ins, pacing satisfaction
    ui/            # Compose screens: onboarding, dashboard, goal editor, settings
    di/            # Hilt modules
  src/test/        # JVM unit tests (scheduler + rotation logic especially)
docs/
  research/        # Evidence library (see docs/research/README.md)
```

Key architectural decisions:

1. **The scheduler is pure Kotlin** (input: clock, user prefs, ledger state; output:
   next slots per channel). This makes the core intelligence testable without
   Android. Channels are dumb pipes that execute what the scheduler decides.
2. **Fatigue-aware rotation engine** scores creatives by `appeal × recency decay ×
   sub-theme diversity` — the app's algorithmic heart. Heavily unit-tested.
3. **Exposure ledger is append-only** (event log), enabling both the rotator and
   future analysis without schema migrations. Sensitive-by-design: stays on device,
   exportable by the user, purgeable in one tap.
4. **No network module in v1.** Remote creative packs (Openverse etc.) are a Phase 3
   separate module with its own INTERNET permission — permission audits must show
   v1 as network-free.

## Feature Phases

### Phase 0 — Foundation (repo + skeleton)
Gradle setup, CI, license headers, conventional commits, this documentation, code
style (ktlint/detekt), Hilt skeleton, empty Compose shell, first test.

### Phase 1 — MVP: "The Billboard" (wallpaper engine)
The flagship feature and clearest embodiment of the thesis.
- Goal creation wizard: pick goal → pick theme pack → if-then anchors → pacing prefs.
- Creative pack ingestion (bundled CC0 images + JSON manifest with license data).
- Fatigue-aware wallpaper rotator via WorkManager.
- Exposure ledger + dashboard ("your campaign at a glance").
- Quiet hours, per-goal toggles, revert-wallpaper flow.
- Ship target: installable APK a stranger could use daily for hydration or nature.

### Phase 2 — "The Campaign" (multi-channel + notifications)
- Image-led notifications with BigPictureStyle, per-goal channels, actions
  (check-in / snooze / fewer-of-these).
- Full scheduling engine: budgets, cooldowns, crowding backoff, fresh-start boosts.
- Check-in loop + simple progress visualization.
- "Why am I seeing this?" transparency affordance.
- Pacing audit screen + "too much" one-tap volume reducer.

### Phase 3 — "The Agency" (adaptivity + richer surfaces)
- Glance widgets, lock-screen-friendly ticker notification.
- On-device self-optimization: time-to-open/dismiss feedback loop adjusts slot times
  (bandit-lite). Micro-randomized copy A/B within safe bounds.
- Temptation-bundle pairing flows; breathing overlay micro-intervention.
- Optional remote creative packs (separate networked module, opt-in).
- Export/backup of user data (JSON/zip), goal graduation flows.

### Phase 4 — Polish & community
- Accessibility pass (TalkBack, reduced motion, contrast).
- Theming (dynamic color previews), alternate goal voices.
- F-Droid packaging, Play listing, CONTRIBUTING polish, i18n extraction.

Phase gates: each phase ends with a tagged release (`v0.1.0` etc.), release notes,
and a "post-mortem" issue reviewing pacing defaults against feedback.

## UX Principles (binding)

1. **Visual-first, copy-light.** Big imagery, one line of copy. Sensory-concrete
   words ("crisp, cold water"), never lectures.
2. **Delectable, not dutiful.** Positive valence only; no fear/shame/guilt. See
   digital-wellbeing.md §3 — these are hard rules with research citations.
3. **User is the boss.** Every channel toggleable, global kill switch always
   reachable, budgets visible. The app never outranks user intent.
4. **Quiet, not pushy.** IMPORTANCE_DEFAULT default, quiet hours sacred, hard
   in-code caps that no setting can push past sane maxima.
5. **Transparent.** "Why am I seeing this?" on every nudge; the research library is
   a first-class citizen of the repo and linked from the app's About screen.
6. **Forgiving.** No punitive streaks by default; fresh-start framing after lapses
   (research: Lally — one missed day doesn't matter; shaming backfires).

## Branching Strategy

Trunk-based with short-lived branches (suits a solo-maintainer OSS project with
occasional contributors and keeps CI simple):

- **`main`** — always releasable. Protected: no force-push, no direct pushes except
  the maintainer's hotfixes via PR. Every merge = conventional-commit message.
- **`feat/<slug>`** — one feature or coherent change, merged via squash-merge PR
  once CI + review checklist pass. Delete branch after merge.
- **`fix/<slug>`**, **`docs/<slug>`**, **`chore/<slug>`** — same pattern.
- **Tags** `v0.x.y` on main mark releases; CHANGELOG.md entries accompany each tag.

Rules: rebase your branch on main before opening a PR; keep PRs atomic and under
~400 lines of diff where practical; conventional commits enforced by CI (see
commit-lint config in `.github/workflows`); PR template includes the AGENTS.md review
checklist as literal checkboxes. Never rewrite `main` history.

Versioning: SemVer-ish for app releases (`vMAJOR.MINOR.PATCH`), with MINOR bumps for
feature phases and PATCH for fixes. `versionCode` monotonic in gradle.

**Released versions are immutable.** Never modify, re-cut, or overwrite an
already-published release (tag, GitHub Release, or attached APK). If a release
turned out wrong, ship a new PATCH version instead. Fixes to released content go
into the next release, never into history rewrites.

## Definition of Done (every PR)

- [ ] Tests for new logic pass; existing suites green (CI = GitHub Actions on
      ubuntu + windows runners: build, ktlint/detekt, unit tests).
- [ ] Docs touched where behavior/design changed (research library too, if the
      evidence base moved).
- [ ] No secrets/personal data/proprietary material (AGENTS.md checklist).
- [ ] Ethical guardrails respected (see digital-wellbeing.md binding rules).
- [ ] Works offline; respects quiet hours; caps enforced in code.
- [ ] Screenshot or manual test notes for UI changes.
- [ ] CHANGELOG entry if user-facing.

## Repo Management System (for future agents)

The system is **docs-driven**: everything needed to resume work is in-repo, in text,
discoverable by grep. Conventions:

- **This file** (DEVELOPMENT.md) = product plan + architecture + workflow. Update it
  in the same PR as any plan-level change.
- **docs/research/** = evidence library. Features cite it; it cites the literature.
- **Issues** = task queue. Each phase has a milestone; each feature has an issue with
  acceptance criteria. Agents work issues, not vibes: check open issues before
  starting work; reference the issue in commits and PRs.
- **CHANGELOG.md** = user-facing history. Keep a Running changes section per phase.
- **.github/** = CI, PR and issue templates embedding the review checklist.
- **Status banner in README** = "Current phase" indicator so newcomers immediately
  know where the project stands.

Agent onboarding sequence: README → AGENTS.md → DEVELOPMENT.md → relevant
docs/research/* for the feature at hand → open issues (look for the current phase
milestone) → write code + tests → PR against the checklist.

## Build & Run (Phase 0 onward)

Requirements: JDK 17+, Android SDK (compileSdk 37, minSdk 26 — pre-WorkManager-quirks
baseline for modern scheduling reliability), Gradle wrapper.

```
./gradlew assembleDebug
./gradlew test
```

Toolchain notes (verified 2026-10): Gradle 9.8.0, AGP 9.4.1 (built-in Kotlin — no
separate kotlin-android plugin), KSP 2.3.12, Hilt 2.60.1, Compose BOM 2026.09.00.
Builds fine on JDK 17 (Temurin, what CI uses) and on Android Studio's JBR 25 for
assemble/test; detekt's bundled analyzer chokes on JDK 25, so run `detekt` under
JDK 17. Copy `local.properties.template` to `local.properties` (gitignored) and
set your SDK path before first build.

Repositories: GitHub `jimspurgeon/retarget`. Issues and PRs only; no email patches.

## Pre-registered decisions (do not silently change)

1. Local-first, no network in v1 (see architecture §3 — the v1 manifest will
   literally lack the INTERNET permission).
2. Streaks OFF by default; fresh-start framing after lapses (never guilt).
3. Quiet hours 22:00–07:00 by default, enforced in code.
4. No analytics SDKs, ever; metrics are local-only and exportable.
5. No fear/shame imagery or copy, ever.
6. Default daily nudge caps enforced in code (notifications ≤3/day/goal,
   overlays ≤1/day) — settings can lower, never raise past hard maxima.
7. Everything open: repo, roadmap, even this reasoning. Trust is the product.
