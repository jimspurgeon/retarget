# Changelog

All notable changes to this project are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/); versioning is SemVer-ish
(`vMAJOR.MINOR.PATCH` — see DEVELOPMENT.md).

## [v0.3.3] - 2026-10-06

### Content curation — hydration & vegetables packs

Second-pass curation under a stricter standard (maintainer decision, Oct 2026):
imagery must show the subject itself — drinkable water / distinct, appetizing
produce — dominant in frame. Every image in both packs was audited with CLIP
zero-shot classification plus manual visual review.

- **Hydration pack (52 images, fully re-curated):** removed all nature-scenery
  sub-themes (streams, waterfalls, waves, dew, ripples), non-water drinks
  (juice, iced coffee, smoothies, cola/amber drinks), and photos with visible
  brand names on bottles. Replaced with drink-focused sub-themes: glasses of
  water, sparkling pours, lemon/cucumber infused pitchers, mint/berry infused
  bottles, condensation bottles. `fetch_creatives.py` quotas now use drink-only
  search terms.
- **Vegetables pack (67 images):** removed prepared dishes, meat, bread,
  person-dominant shots, flowering plants, leaf-only textures, fruit-only
  baskets (strawberries, apples, pinecones), empty bowls, and unripe/unappetizing
  produce. Refilled with produce-explicit search terms (bell peppers, carrots &
  broccoli, salad bowls, heirloom tomatoes, market stalls).
- `fetch_creatives.py` fixes: existing manifest entries now count toward
  sub-theme quotas (refills fetch only the deficit), and an in-run duplicate
  guard prevents the same photo entering via two search terms.
- **Release policy (repo-wide rule):** released versions are immutable — never
  overwrite or re-cut a published release; ship a new PATCH version instead.
  Documented in DEVELOPMENT.md.
- Test updated: plant-based candidate pool is now 127 (fruit 60 + vegetables 67).

## [v0.3.2] - 2026-10-06

### Hotfix — notification & wallpaper channels were silently dead

**Delivery fixes (PR #19):**
- **Notifications never fired**: `NotificationDeliveryWorker` crashed with
  `NoSuchElementException` (`copyPool.random()` on an empty list — bundled-pack
  creatives carried no copy lines) and WorkManager marked the work terminally
  FAILED after one attempt. Fixed by the new `NudgeCopyCatalog` (theme-matched copy
  lines for every bundled pack) plus a defensive `randomOrNull()` fallback.
- **Wallpaper never changed**: `WallpaperManager.setBitmap()` threw
  `SecurityException` because `SET_WALLPAPER` was never declared in the manifest;
  the rotation worker retried forever without applying anything. Permission now
  declared (normal install-time permission).
- Removed the incorrect `BIND_JOB_SERVICE` declaration from `CheckInService`
  (system-held permission made the service unstartable by the app itself).
- Verified on physical device (Pixel 10 / GrapheneOS / Android 17): cold start
  delivers both a notification and a wallpaper rotation within seconds; both
  exposures recorded in the ledger; zero crashes.

**Content:**
- **Added vegetables creative pack** (54 vibrant images: farmers-market stalls,
  heirloom tomatoes, garden-harvest baskets, leafy-greens macros, rainbow peppers,
  artisan salads). The "More Vegetables" preset now serves its own imagery instead
  of reusing the fruit pack.

---

## [v0.3.1] - 2026-10-06

### Hotfix — Android 17 startup crash + hydration content gap

**Crash fix:**
- Fixed startup crash on Android 17 (Pixel 10 / GrapheneOS) caused by blocking Room queries
  running on the main thread in `DashboardViewModel` (`IllegalStateException: Cannot access database
  on the main thread`). Flows now dispatch to `Dispatchers.IO` via `.flowOn`.
- Verified on physical device: cold start survives with no crash.

**Content gap closure:**
- **Added hydration creative pack** (60 images across 6 sub-themes: sparkling water pours, mountain
  streams, ocean waves, morning dew macros, water ripples, citrus-infused water). Previously,
  the wallpaper channel silently did nothing for hydration goals because no bundled images existed
  for the HYDRATION theme.
- Wallpaper rotation now works end-to-end for hydration. Expect updates within quiet hours (7 AM–10 PM)
  and ~8h rotation cadence.

**Known issues (deferred):**
- Wallpaper may not change immediately if currently in quiet hours (22:00–07:00) or if no active goal
  with wallpaper enabled exists. Complete onboarding and verify goal settings.

---

## [v0.3.0] - 2026-10-05

### Phase 2 "The Campaign" — notifications + scheduler
Multi-channel nudge delivery with strict frequency budgets, transparency, and user control.

**What's new:**
- Image-led notifications (BigPictureStyle) for user-chosen goals.
- `NudgeScheduler` engine: fresh-start boosts, cross-channel crowding backoff.
- Check-in tracking: tap-to-log behavior from notifications.
- "Why am I seeing this?" transparency on every nudge.
- Dashboard pacing stats: exposures vs. check-ins per channel.
- Settings: per-goal channel toggles with immediate WorkManager cancellation.

**Under the hood:**
- Pure-Kotlin schedulers (testable without Android): `NotificationPolicy`, `NudgeScheduler`.
- Room DAOs: `CheckInDao`, `ExposureLedger` channel extensions.
- WorkManager integration: `NotificationDeliveryWorker` schedules subsequent slots.
- Creative image cache (`CreativeImageCache`) for fast notification rendering.
- User-testing prep docs in `docs/testing/`.

---

## [v0.2.0] - 2026-10-04

### Phase 1 MVP "The Billboard" — wallpaper engine

First functional release: wallpaper-based nudges for personal goals.

- Goal creation wizard with 4 preset campaigns (Hydration, Fresh Air, More Fruit, More Vegetables).
- Wallpaper engine: fatigue-aware creative rotation with sub-theme diversity.
- Exposure ledger (Room) + dashboard campaign state.
- Settings with quiet hours (22:00–07:00) + channel toggles.
- Creative packs: `fresh-air` (61 images), `fruit` (60 images) — hand-curated from Unsplash, license metadata validated by `checkCreativeLicenses` gate.
- Local-first: no INTERNET permission, no analytics.
- Gatekeeper-reviewed. 64 tests green.

---

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
