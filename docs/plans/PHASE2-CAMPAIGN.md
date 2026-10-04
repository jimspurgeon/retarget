# Phase 2: "The Campaign" — Notifications + Scheduler Design Doc

**Status:** Planning (Major change — AGENTS.md §5.1 design gate)  
**Phase context:** DEVELOPMENT.md Phase 2 — extends MVP wallpaper engine to multi-channel delivery with notifications, full scheduling engine, and check-in loop.  
**Ethical stance:** User-initiated, reversible nudges; no dark patterns; local-first data. See AGENTS.md §2.

---

## 1. Executive Summary

Phase 2 transforms the single-channel wallpaper MVP into a true multi-channel "advertising campaign" for user goals. The centerpiece is **notifications** — image-led BigPictureStyle nudges that borrow ad creativity but respect strict frequency budgets, quiet hours, and user control. We extend the existing scheduler pattern (pure Kotlin, testable) from wallpaper to notifications, integrate the exposure ledger for cross-channel tracking, and reuse the fatigue-aware CreativeRotator for notification creative selection.

This doc covers:
- Notification channel design (local-only, BudgetPolicy integration)
- Scheduler evolution: wallpaper pattern → general-purpose slot engine
- ExposureLedger extension for notification impressions
- Creative selection (reuse CreativeRotator with minor adapters)
- Revert/opt-out flows (ethical guardrails compliance)
- Milestone breakdown into worker-dispatchable tasks
- Risks and open questions for maintainer review

---

## 2. Notification Channel Design

### 2.1 Architecture Principle

**Channels are dumb pipes.** The scheduler computes *when* and *what*; the notification channel executes delivery. This keeps logic testable in pure Kotlin and separates concerns (DEVELOPMENT.md architecture §2).

```
Scheduler (pure Kotlin)
    ├─ Computes next slots per channel
    ├─ Enforces BudgetPolicy caps & cooldowns
    └─ Returns (creative, channel, scheduledTime)

NotificationChannel (Android)
    └─ Renders BigPictureStyle notification
    └─ Records exposure via ledger
    └─ Handles actions (check-in, snooze, opt-down)
```

### 2.2 Notification Manager Integration

**Implementation surface:** New `NotificationChannel` class under `app/src/main/kotlin/com/retarget/channels/notification/`.

**Key decisions:**
- Use Android's `NotificationManager` with one channel per goal (follows Phase 1 pattern of isolation).
- Style: `BigPictureStyleNotification` — image-led as per UX principle "visual-first, copy-light".
- Priority: `IMPORTANCE_DEFAULT` (no heads-up) unless user explicitly opts into higher interruptiveness.
- Actions per notification:
  - Check-in (primary action — converts impression to goal behavior log)
  - Snooze 2h (respects cooldown policy)
  - "Fewer like this" (one-tap volume reduction — ethical guardrail compliance)

**Code sketch (placeholder):**
```kotlin
// app/src/main/kotlin/com/retarget/channels/notification/NotificationChannel.kt
class NotificationChannel(private val context: Context) {
    fun deliver(notification: NotificationSpec) {
        // Build BigPictureStyle, enqueue with NotificationManager
        // Record exposure via ledger
    }
}

data class NotificationSpec(
    val goalId: Long,
    val creative: Creative,
    val actions: NotificationActions,
)
```

**Permission justification:**
- No new permissions required beyond Phase 1 (POST_NOTIFICATIONS added in Android 13+ is standard system permission, documented in manifest).
- Justification text for users: "Notifications deliver your chosen goal reminders during active hours only. You control frequency and can turn them off anytime."

### 2.3 Quiet Hours Respect

Reuse `BudgetPolicy.isQuietHour()` — hard-coded defaults 22:00–07:00 per DEVELOPMENT.md pre-registered decision #3.

**Enforcement layers (defense in depth):**
1. Scheduler filters out quiet-hour slots entirely.
2. NotificationChannel double-checks before dispatch.
3. User setting can only *lower* caps, never raise past `BudgetPolicy.NOTIFICATION_HARD_MAX_PER_DAY_PER_GOAL` (currently 3/day/goal).

### 2.4 Budget & Pacing via BudgetPolicy

Extend `BudgetPolicy.canDeliver()` to track notification-specific counters:

**Current state:** Already enforces per-channel daily caps, cooldown after dismissal, saturation detection.

**Required extensions:**
- Add `channel: Channel` parameter to counters (already there, but notification needs Room backing).
- Persist notification send count per goal per day (new DAO on `CampaignStats` or ledger view).
- Scheduler queries `canDeliver()` before scheduling each slot.

**Research basis:** interruption-timing.md §1, §7 — hard caps prevent dismiss-without-read spiral; cooldown prevents saturation signaling.

---

## 3. Scheduler Evolution

### 3.1 Current State (Phase 1)

`WallpaperScheduler` is a thin WorkManager wrapper around `WallpaperRotationPolicy`. Pure Kotlin logic (`WallpaperRotationPolicy`) checks `BudgetPolicy.isQuietHour()` before allowing rotation.

**Strengths:** Separation of concerns, testable policy, idempotent scheduling.

**Limitations:** Tied to wallpaper channel only; doesn't handle per-goal pacing, crowding backoff, or fresh-start boosts.

### 3.2 Target State (Phase 2)

New `NudgeScheduler` class that:
- Accepts active goals with enabled channels.
- Computes next slots per channel using policies (wallpaper, notification, overlay).
- Applies fresh-start boosts from `FreshStartCalendar.boostMultiplier()`.
- Respects cross-channel crowding backoff (don't fire notification within X min of wallpaper swap).

**Architecture:**
```kotlin
object NudgeScheduler {
    /** Compute next delivery slots for all active goals/channels. */
    fun computeSlots(
        activeGoals: List<GoalEntity>,
        nowMs: Long,
        ledger: ExposureLedger,
        random: Random = Random.Default,
    ): List<Slot>
}

data class Slot(
    val goalId: Long,
    val channel: Channel,
    val creative: Creative,
    val scheduledTimeMs: Long,
    val priority: Double, // boosted by FreshStartCalendar if applicable
)
```

**Migration plan:**
1. Extract common slot computation logic from `WallpaperRotationPolicy` into base `ChannelPolicy` interface.
2. Implement `NotificationPolicy` alongside existing `WallpaperRotationPolicy`.
3. `NudgeScheduler` coordinates multiple policies, applies cross-channel constraints.
4. Retain `WallpaperScheduler` as backward-compatible facade for Phase 1.

### 3.3 Scheduling Mechanism

**WorkManager strategy (Android):**
- Each goal/channel gets its own periodic work tag (e.g., `"retarget_notification_<goalId>"`).
- `NudgeScheduler.computeSlots()` determines next execution time.
- WorkManager enqueues with `setInitialDelay()` until slot time.
- Worker wakes up, re-evaluates `canDeliver()`, delivers or reschedules.

**Alternative considered:** AlarmManager for precise timing. **Rejected** — WorkManager handles Doze/idle more reliably; precision loss acceptable given ±30–45 min windows per interruption-timing.md §6.

---

## 4. ExposureLedger Integration

### 4.1 Cross-Channel Tracking

**Current state:** `RoomExposureLedger` already supports `Channel` enum with `WALLPAPER` values.

**Extension required:**
- Add `NOTIFICATION` and `OVERLAY` enum values (already exist per CreativeRotator.kt).
- Query surface: add methods to query exposures by channel (e.g., `exposuresByChannel(channel: Channel)`).
- Dashboard screen aggregates by channel for "pacing audit" feature.

**Data model:** No schema change needed — `Channel` enum already defined, `ExposureEntity.channel` column accepts it.

### 4.2 Notification Impressions

Each notification delivery logs exposure same as wallpaper:
```kotlin
ledger.recordExposure(
    creativeId = creative.id,
    subTheme = creative.subTheme,
    channel = Channel.NOTIFICATION,
    atMs = System.currentTimeMillis(),
)
```

**Dashboard use case:** Show "notification impressions today: X/Y" where Y is user's cap.

---

## 5. Creative Selection for Notifications

### 5.1 Reuse CreativeRotator

**Decision:** No new rotation algorithm. The existing `CreativeRotator.score()` applies equally to notifications.

**Rationale:**
- Same fatigue mechanics apply (wearout, rest recovery, diversity).
- Avoids duplication; single source of truth for scoring.
- Research basis identical (mere exposure effect, advertising-psychology.md §4).

**Adapter required:**
- Notification channel provides filtered candidate pool (goals → creative packs → creatives).
- `CreativeRotator.selectNext()` called with notification-suitable creatives only.
- `Creative.copyPool` used differently: notification displays one line from pool; wallpaper may not.

### 5.2 Copy Selection Policy

**Current:** `Creative.copyPool` is a `List<String>` — pool of candidate one-liners.

**Phase 2 behavior:**
- Rotator returns `Creative`; notification channel selects one copy line.
- Selection method: round-robin across pool OR fatigue-weighted (future enhancement).
- For MVP: simple rotation by index = `exposures % pool.size`.

**Example:**
```kotlin
val copyLines = creative.copyPool
val currentCopy = copyLines[notificationCountForCreative % copyLines.size]
```

---

## 6. Revert/Opt-Out Flows

### 6.1 Ethical Guardrails Compliance (AGENTS.md §2)

**Requirements:**
1. **User-initiated only** — notifications disabled by default (`CampaignSettings.notificationEnabled = false`).
2. **Reversible** — per-channel toggle in Settings; global kill switch available.
3. **Transparent** — "Why am I seeing this?" explains goal, timing rationale, budget usage.
4. **No dark patterns** — opt-out as easy as opt-in; no confirm-shaming.

### 6.2 Specific Flows

**Per-channel disable:**
- Settings screen: checkbox "Enable notification nudges for [goal]".
- On uncheck: `NotificationScheduler.cancelUniqueWork(goalTag)` immediately.
- Confirm: Toast "Notification nudges paused. Tap to re-enable."

**One-tap volume reduction:**
- Notification action: "Fewer like this".
- Behavior: Decrease `notificationTargetsPerDay` by 1, floor at 0.
- UI feedback: "Reduced to X/day. Change in Settings anytime."

**Full campaign deletion:**
- Goal screen: "Delete this goal" → confirms → DAO deletes goal entity.
- Cascade: WorkManager cancels all related jobs; ledger exposures retained (analytics value) but anonymized in dashboard views.

### 6.3 Transparency Screen

**Feature:** "Why am I seeing this?" on notification tap or dashboard info icon.

**Content:**
- Goal name and theme.
- Technique used: "Scheduled at your morning routine time."
- Budget status: "3/3 notifications today (your limit)."
- Link to settings: "Adjust frequency or turn off."

---

## 7. Milestone Breakdown

**Classification:** Major change (per AGENTS.md §5.2 — spans multiple modules, touches persistence, adds nudge behavior).

**Breakdown into worker-dispatchable milestones:**

### Milestone 2.1: Notification Infrastructure (~40–60 lines)
- Create `NotificationChannel` class with BigPictureStyle rendering.
- Define `NotificationSpec` data class.
- Set up per-goal NotificationManager channels.
- Add POST_NOTIFICATIONS permission to manifest (Android 13+).
- Unit test: mock notification builder validates fields.

**Acceptance:** Can render a notification with image + copy + actions in emulator.

### Milestone 2.2: Notification Scheduling Policy (~30–50 lines)
- Create `NotificationPolicy.kt` mirroring `WallpaperRotationPolicy`.
- Integrate with `BudgetPolicy.canDeliver()`.
- Respect quiet hours, daily caps, cooldowns.
- Unit test: policy blocks during quiet hours, allows within caps.

**Acceptance:** Policy returns correct allow/deny for various inputs.

### Milestone 2.3: NudgeScheduler Engine (~80–120 lines)
- Implement `NudgeScheduler.computeSlots()` coordinating multiple channels.
- Apply `FreshStartCalendar` boosts to slot priorities.
- Handle cross-channel crowding backoff (configurable gap between notifications).
- Unit test: slots computed correctly for mixed goals/channels.

**Acceptance:** Given active goals, produces sorted slot list with correct timing.

### Milestone 2.4: WorkManager Integration (~40–60 lines)
- Create `NotificationDeliveryWorker` analogous to `WallpaperRotationWorker`.
- Wire `NudgeScheduler` to enqueue next slot after each delivery.
- Cancel worker on channel disable or goal deletion.
- Instrument test: verify exposure logged after successful delivery.

**Acceptance:** End-to-end: goal enabled → notification appears at computed time.

### Milestone 2.5: ExposureLedger Extensions (~20–30 lines)
- Add `exposuresByChannel()` query method to `ExposureDao`.
- Update dashboard UI to show per-channel pacing stats.
- Unit test: query returns correct counts.

**Acceptance:** Dashboard shows "Notifications: 2/3 today".

### Milestone 2.6: Opt-Out & Transparency UI (~60–90 lines)
- Settings screen: per-goal channel toggles with immediate WorkManager cancellation.
- "Fewer like this" notification action handler.
- "Why am I seeing this?" info screen/modal.
- Manual test: verify all flows are discoverable and reversible.

**Acceptance:** User can disable notifications, reduce frequency, and understand rationale.

### Milestone 2.7: Check-In Loop (~40–60 lines)
- "Check-in" notification action records behavior in new `CheckInDao` (Room table).
- Dashboard shows check-in count vs. exposure count (acceptance rate).
- Simple progress bar visualization.

**Acceptance:** Tapping check-in increments counter visible on dashboard.

### Milestone 2.8: E2E Testing & Polish (~30–50 lines)
- Integration tests: scheduler → worker → notification → ledger.
- Test edge cases: quiet hours crossing, day rollover, DST boundaries.
- Documentation updates: README mentions notification channel.

**Acceptance:** All CI tests pass; no known blockers.

---

## 8. Risks and Open Questions

### Risk 1: WorkManager Precision Under Doze Mode
**Impact:** Scheduled slots may drift beyond ±30 min tolerance.  
**Mitigation:** Document limitation to users; for critical timing, consider AlarmManager fallback (extra complexity).  
**Decision-shaped?** Yes — if user expectation is "precise morning reminder," drift may frustrate.

**Options:**
- A. Accept WorkManager drift (±30 min acceptable per timing research). Cost: low. Risk: some users may miss "perfect timing."
- B. Hybrid: use AlarmManager for morning slots, WorkManager for flexible ones. Cost: ~50 lines + maintenance. Risk: more code surface.

**Recommendation:** Start with WorkManager only (option A); monitor user feedback. If complaints arise, migrate to hybrid.

### Risk 2: Notification Image Loading Performance
**Impact:** Large images slow notification rendering or cause OOM.  
**Mitigation:** Pre-resize images during creative ingestion pipeline (Phase 1). Cache resized versions.  
**Decision-shaped?** Partially — if Phase 1 didn't cover image resizing, Phase 2 must add it.

**Action item:** Verify Phase 1 bundled packs include optimized (≤500KB) images. If not, add resize step to Milestone 2.1.

### Risk 3: Per-Goal Notification Channels Explosion
**Impact:** Android 8.0+ requires one channel per group; 20 goals = 20 channels.  
**Mitigation:** Use single channel with varying notification ID, or group by goal category.  
**Decision-shaped?** Yes — affects notification grouping behavior.

**Options:**
- A. One channel per goal (cleaner isolation, more clutter). Cost: trivial.
- B. Single "Retarget Nudges" channel with goal-themed groups. Cost: ~20 lines. Better user experience.

**Recommendation:** Option B — single channel reduces user confusion about "why so many Retarget channels."

### Risk 4: Cross-Channel Crowding Backoff Complexity
**Impact:** Multiple channels firing in quick succession feels spammy.  
**Mitigation:** Simple rule (e.g., min 60 min between any channels) in `NudgeScheduler`.  
**Decision-shaped?** Yes — affects user experience significantly.

**Default proposal:** 60-minute minimum gap between different channel deliveries for same goal. Configurable in settings (floor at 30 min).

### Open Question 1: Check-In Persistence Model
**Question:** Should check-ins be stored as separate entities or as a special exposure type?  
**Implication:** Schema design now affects future analytics.  
**Decision-shaped?** Yes — harder to migrate later.

**Options:**
- A. Separate `CheckInEntity(goalId, atMs, notes?)`. Clean separation.  
- B. Reuse exposure ledger with `eventType: CHECKIN`. Unified but semantically mixed.

**Recommendation:** Option A (separate table) — clearer domain model; check-ins are outcomes, not impressions.

### Open Question 2: "Why Am I Seeing This?" Granularity
**Question:** How much technical detail to expose? (e.g., "your FreshStart boost was +0.15" vs. "today is Monday, a fresh-start day.")  
**Implication:** Transparency vs. overwhelming users.

**Recommendation:** Human-readable only ("Today's a Monday fresh-start day, so you're seeing more nature imagery."). Hide numeric multipliers unless user taps "More details."

---

## 9. Design Decisions Logged (Non-Gated)

During planning, the following choices were made and logged (AGENTS.md §5.1.1):

| Decision | Default Applied | Alternative Rejected | Rationale |
|----------|-----------------|---------------------|-----------|
| Notification style | BigPictureStyle | Compact inbox style | Visual-first UX principle; imagery drives attention |
| Copy selection | Round-robin from pool | Fatigue-weighted | Simpler MVP; weighted can be added post-data |
| Per-goal vs single channel | Single channel | One per goal | Reduces user confusion; easier to manage |
| WorkManager vs AlarmManager | WorkManager only | Hybrid approach | Start simple; migrate if drift complaints |
| Check-in storage | Separate table | Reuse exposure ledger | Cleaner domain model |

---

## 10. Verification Plan

**Unit tests:**
- `NotificationPolicyTest` — quiet hours, caps, cooldown enforcement.
- `NudgeSchedulerTest` — slot computation, fresh-start boosts, crowding backoff.
- `ExposureLedgerRoomTest` — extended queries for channel filtering.

**Integration tests:**
- `NotificationDeliverySmokeTest` — end-to-end: goal enabled → worker → ledger entry.
- `CrossChannelBackoffTest` — verifies 60-min gap between channels.

**Manual tests:**
- Install APK, enable hydration goal + notifications.
- Wait for slot time; verify notification renders with image, copy, actions.
- Tap check-in; verify dashboard count increments.
- Disable notifications; verify WorkManager job cancelled.

---

## 11. References

- DEVELOPMENT.md Phase 2 feature description
- AGENTS.md ethical guardrails (§2) and agent review protocol (§5)
- docs/research/digital-wellbeing.md — interruption tolerances, ethical line
- docs/research/interruption-timing.md — timing defaults, saturation signals
- `BudgetPolicy.kt`, `FreshStartCalendar.kt`, `CreativeRotator.kt` — existing code patterns

---

## 12. Appendix: Change Classification

This design doc represents a **Major** change per AGENTS.md §5.2:
- Alters nudge behavior (adds notification channel).
- Touches persistence (ledger queries, potential new check-in table).
- Spans multiple logical commits (infrastructure → policy → scheduler → UI).
- Exceeds ~400 lines estimated diff across Milestones 2.1–2.8.

**Deep-scrutiny requirements apply** when implementation begins: adversarial self-review, judgment-call documentation, review kit with hotspot annotations.

---

*End of design doc.* Ready for maintainer review and milestone assignment.
