# Phase 3: "The Agency" — Adaptivity + Richer Surfaces Design Doc

**Status:** Draft for review (Major change — AGENTS.md §5.1 design gate)
**Phase context:** DEVELOPMENT.md Phase 3 — extends the multi-channel campaign
engine (Phase 2) with richer delivery surfaces, on-device adaptivity, and data
portability.
**Ethical stance:** User-initiated, reversible nudges; no dark patterns;
local-first data; adaptivity bounded by the same hard caps as Phase 2. See
AGENTS.md §2 and docs/research/digital-wellbeing.md.

---

## 1. Executive Summary

Phase 3 turns the fixed campaign engine into a personal *agency*: surfaces beyond
wallpaper and heads-up notifications (widgets, lock-screen ticker), gentle
self-optimization from on-device feedback, and data portability. The guiding
constraint stays the same — the user is the boss. Every new surface is
opt-in and toggleable; adaptive logic may only *reduce* interruption, never
raise delivery past BudgetPolicy maxima.

Proposed milestone ordering (risk-first, ethics-gated):

1. **M3.1 — Data export/backup** (foundation for trust; smallest surface).
2. **M3.2 — Glance home-screen widget** (goal glanceable + one-tap check-in).
3. **M3.3 — Lock-screen-friendly ticker notification** (low-interruption channel).
4. **M3.4 — Feedback loop: bandit-lite slot adjustment** (uses export data
   pipeline; heavily unit-tested; caps immutable).
5. **M3.5 — Temptation-bundle pairing flow + breathing overlay**.
6. **M3.6 — Remote creative packs (separate networked module, opt-in)** —
   explicitly last, only after user-testing validates core loops.

This doc covers each milestone's scope, risks, acceptance criteria, and the
open questions that need maintainer decisions before implementation dispatch.

---

## 2. Milestone 3.1 — Data Export & Backup

### Scope
- Settings screen action: "Export my data" → JSON (or zip with images manifest)
  via the system share sheet (ACTION_CREATE_DOCUMENT / ActivityResult).
- Export payload: goals, campaign settings, exposure ledger events, check-ins.
  No creative binaries (they're re-downloadable repo assets with license
  metadata; manifest references only).
- "Delete everything" one-tap purge (already partly present) verified against
  export (export → purge → re-import sanity).

### Ethics/privacy gates
- Export runs entirely on-device; no network (pre-registered decision #1).
- File goes wherever the user points it; app keeps no copy beyond cache.

### Acceptance
- Export produces valid JSON a future import (M3.1.1, optional) can read.
- Purge removes all Room rows + WorkManager jobs + wallpaper revert offered.
- Unit tests: serializer round-trip on synthetic data.

---

## 3. Milestone 3.2 — Glance Home-Screen Widget

### Scope
- Single-sized widget: current goal + today's pacing (e.g., "2 of 3 nudges"),
  latest creative as background, one-tap check-in button.
- Updated by WorkManager ticks aligned with existing scheduler (no extra wakeups).
- Widget add flow sets per-goal widget preference (first added goal wins for MVP).

### Constraints
- No new permissions.
- Widget must degrade gracefully without data (onboarding hint state).

### Acceptance
- Widget renders goal, pacing, creative; check-in from widget increments ledger.
- Robolectric test for glance state mapping.

---

## 4. Milestone 3.3 — Lock-Screen Ticker Channel

### Scope
- Low-key channel: silent, low-priority ongoing notification ("ticker") shown
  on lock screen; no heads-up, no sound, no vibration.
- New `Channel.LOCK_SCREEN_TICKER` in BudgetPolicy with its own (low) hard cap.
- Coordinates through NudgeScheduler's existing crowding backoff.

### Acceptance
- Scheduler treats ticker as a first-class channel in slot computation tests.
- Manual test on emulator: ticker visible on lock screen, absent during quiet
  hours.

---

## 5. Milestone 3.4 — Bandit-Lite Self-Optimization

### Scope
- On-device only feedback loop: time-to-dismiss, time-to-check-in, and
  "fewer like this" events adjust *slot timing* and creative sub-theme mix
  per goal (ε-greedy or Thompson-lite over slot buckets).
- Explicit, user-visible statement of what adapts and what never does
  (caps/quiet hours immutable — pre-registered decisions #3/#6).
- Telemetry surfaced in Transparency screen ("your evening nudges work best").

### Risks
- Overfitting to early noise → conservative priors, min-observation floors
  before any adaptation, and a hard "reset learning" setting.
- Privacy: all learning state in Room; included in export (M3.1).

### Acceptance
- Simulation tests: bandit converges to better slots on synthetic response
  curves; never exceeds caps; resets cleanly.

---

## 6. Milestone 3.5 — Temptation Bundling & Breathing Overlay

### Scope
- Pairing flow: user pairs a "want" (existing goal imagery) with a "should"
  (target behavior); overlay activity shows paired creative with a 30-second
  breathing micro-intervention (overlay channel ≤1/day per existing decision #6).
- Overlay is dismissible at any moment; no full-screen locks.

### Acceptance
- Overlay renders, breathing animation runs, exposure logged to ledger,
  dismissal recorded as feedback for M3.4.

---

## 7. Milestone 3.6 — Remote Creative Packs (Optional, Last)

### Scope
- Separate Gradle module `pack-remote` with its own INTERNET permission; main
  app stays network-free (pre-registered decision #1 preserved).
- Opt-in UI explaining exactly what network access enables; uninstall-safe
  (bundled packs remain default).

### Acceptance
- Manifest permission audit: base APK still lacks INTERNET.
- Fetch → validate (same license gate as bundled packs) → ingest pipeline.

---

## 8. Decisions (maintainer-approved 2026-10-05)

1. **Import support in M3.1:** Export-only for v0.4.0; import (restore) deferred to v0.4.1.
   - *Rationale:* Export fulfills the local-first trust promise; import adds schema-versioning complexity prematurely.
   - *Action:* Milestone 3.1 scope = export only.

2. **Widget sizes:** Single medium widget MVP (no small widget family).
   - *Rationale:* Glance API is still maturing; one size minimizes surface area, proves concept, avoids layout fragmentation.
   - *Action:* Milestone 3.2 scope = single Glance medium widget.

3. **Bandit aggressiveness:** ε-greedy (ε=0.1) with floor of 20 observations per bucket.
   - *Rationale:* Simplicity, debuggability, and provable restraint trump marginal convergence speed; explanation in Transparency screen is one sentence.
   - *Action:* Milestone 3.4 scope = ε-greedy with 20-observation floor; no Thompson sampling.

4. **Ticker channel default:** Off by default (opt-in via onboarding/settings).
   - *Rationale:* Lock-screen presence is the highest-friction channel; default-off preserves consent ethos, reduces first-run churn.
   - *Action:* Milestone 3.3 default = disabled; discovery prompt in onboarding only.

---

## 9. Open Questions for Maintainer

- DEVELOPMENT.md Phase 3 scope; pre-registered decisions #1–#7.
- docs/research/interruption-timing.md — timing/adaptivity evidence.
- docs/research/digital-wellbeing.md — ethical bounds for adaptivity.
- Phase 2 design doc (docs/plans/PHASE2-CAMPAIGN.md) — established scheduler
  patterns this phase extends.

---

*Status: awaiting maintainer review; milestones will be turned into issues on
approval.*
