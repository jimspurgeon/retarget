# Notification Scheduling Strategy (Phase 2)

**Related**: docs/plans/PHASE2-CAMPAIGN.md section 3.3 (Milestone 2.4: WorkManager Integration)  
**Classification**: Technical design document (implementation complete)  
**Last updated**: 2024-10-04

---

## 1. Scheduling Mechanism Overview

The notification delivery system uses WorkManager for scheduling nudge notifications. This document describes the scheduling strategy, drift tolerance guarantees, and the trade-offs that led to this design.

### Key Components

1. **NudgeScheduler**: Pure Kotlin logic that computes optimal delivery slots based on:
   - Active goals with notification enabled
   - BudgetPolicy caps and cooldowns
   - FreshStartCalendar boosts
   - Creative fatigue scores via CreativeRotator

2. **NotificationDeliveryWorker**: Android WorkManager worker that:
   - Wakes up at scheduled times
   - Re-validates delivery eligibility
   - Delivers notification via NotificationChannel
   - Records exposure in Room database
   - Reschedules next slot

3. **NotificationSchedulerManager**: Lifecycle manager that:
   - Observes goal activation changes via Flow
   - Auto-starts/stops WorkManager periodic work
   - Ensures user-initiated nudges only (AGENTS.md §2)

---

## 2. Scheduling Cadence and Timing

### 2.1 Periodic Check Interval

**Default**: Every 2 hours (CHECK_INTERVAL_HOURS = 2L)

**Rationale**:
- Balances timeliness with battery efficiency
- User-configurable daily cap (default 3/day) suggests ~8-hour spacing, but 2-hour checks allow flexibility for:
  - FreshStartCalendar boosts (higher frequency on "important" days)
  - User-configured lower caps
  - Cooldown and saturation avoidance

**Trade-offs considered**:
- **Every 30 minutes**: Too frequent; unnecessary wake-ups when quiet hours or caps block delivery
- **Every 6 hours**: May miss boost opportunities; slower response to goal activation
- **Every 2 hours**: Chosen compromise; aligns with PHASE2-CAMPAIGN.md Risk 1 drift tolerance

### 2.2 Slot Time Calculation

**Mechanism**:
```kotlin
fun computeNextSlotTime(nowMs: Long, random: Random): Long {
    val baseIntervalMs = 2 * 60 * 60 * 1000L // 2 hours
    val jitterRangeMs = 30 * 60 * 1000L // ±30 minutes
    val jitter = (random.nextDouble() * 2 - 1) * jitterRangeMs
    return nowMs + baseIntervalMs + jitter.toLong()
}
```

**Drift tolerance**: ±30 minutes acceptable per PHASE2-CAMPAIGN.md Risk 1

**Justification**:
- Interruption timing research (docs/research/interruption-timing.md §6) indicates ±30–45 minute windows are acceptable for ambient reminders
- WorkManager's Doze/idle mode handling is superior to AlarmManager's precision requirements
- User experience focus is "daily cadence and context" not "pinpoint timing"

**Alternative rejected**: AlarmManager hybrid approach
- Would add ~50 lines of complexity
- Not justified by user needs (no requirement for "precise 9:00 AM reminder")
- Can be added later if user feedback demands it

---

## 3. WorkManager vs AlarmManager Decision

### Background

WorkManager provides:
- Guaranteed execution even after app restarts or device reboots
- Built-in Doze/idle mode handling
- Automatic backoff for failures
- Constraint-based scheduling (battery, network)

But lacks:
- Exact timing guarantees (drift possible)

AlarmManager provides:
- Precise timing (even under Doze with setExactAndAllowWhileIdle)
- But requires manual retry logic, boot receiver, etc.

### Decision Analysis

| Criterion | WorkManager | AlarmManager | Winner |
|-----------|-------------|--------------|--------|
| Precision | ±30-45 min drift | Exact | AlarmManager |
| Battery efficiency | Excellent (Doze-aware) | Moderate (manual optimization) | WorkManager |
| Code complexity | Low (PeriodicWorkRequest) | High (BroadcastReceiver, BootCompleted, etc.) | WorkManager |
| Reliability under Doze | Excellent | Good (setExactAndAllowWhileIdle) | Tie |
| Maintenance burden | Minimal (AndroidX) | Higher (custom implementation) | WorkManager |

**Final decision**: WorkManager only (PHASE2-CAMPAIGN.md Risk 1 Option A)

**Risk mitigation**:
- Document drift tolerance to users in "Why am I seeing this?" transparency screen
- If user complaints arise about timing accuracy, migrate to hybrid (WorkManager for flexible slots, AlarmManager for critical morning reminders)

---

## 4. Reschedule Pattern

After successful delivery, the worker:

1. Calls `NudgeScheduler.rescheduleNotification()` with:
   - Goal ID, creative delivered, settings, ledger, current timestamp
   - Checks if more deliveries allowed today (caps, cooldowns)
   - Returns null if cap reached, otherwise returns next slot

2. If slot returned:
   - Calculates `initialDelay = slotTimeMs - nowMs`
   - Enqueues new work with `setInitialDelay(delay, TimeUnit.MILLISECONDS)`
   - Logs scheduled time for debugging

3. If no slot:
   - Logs "cap reached for today"
   - No new work enqueued
   - Will be restarted by next periodic check (in case of manual setting changes)

**Edge case handling**:
- Quiet hours: Reschedule skips if current time is in quiet window
- Cap reached: Reschedule returns null; periodic worker will retry tomorrow
- Goal deactivated: Worker sees empty goal list; exits without rescheduling

---

## 5. Cross-Channel Considerations

Currently, notifications are the only additional channel beyond wallpaper. Future expansion (Overlay channel) will require:

**Crowding backoff rule**: Minimum 60-minute gap between different channel deliveries for same goal (PHASE2-CAMPAIGN.md Risk 4)

**Implementation plan**:
- Add `lastDeliveryByChannel(goalId: Long, channel: Channel)` query to ledger
- Modify `NudgeScheduler.computeSlots()` to skip slots within backoff window
- Configurable via settings (floor at 30 minutes per research)

**Not implemented yet**: Milestone 2.7 (Check-In Loop) and 2.8 (E2E Testing) may refine this requirement.

---

## 6. Testing Coverage

### Unit Tests
- `NudgeSchedulerTest` (not yet created) — slot computation logic
- `NotificationSchedulerManagerTest` (partial via integration test) — lifecycle management

### Integration Tests
- `NotificationSchedulerManagerIntegrationTest` — verifies:
  - Scheduler starts/stops with goal activation
  - WorkManager tags and states
  - Refresh behavior after goal changes

### Manual Tests Required
- Install APK, enable hydration goal + notifications
- Wait for slot time; verify notification renders with image, copy, actions
- Tap check-in; verify dashboard count increments
- Disable notifications; verify WorkManager job cancelled
- Observe drift over multiple days; confirm within ±30 min tolerance

---

## 7. Known Limitations and Future Enhancements

### Limitations

1. **Timing drift**: ±30 min acceptable but may frustrate users expecting precise timing
2. **No timezone handling**: Assumes system default; DST transitions handled implicitly by Java time APIs
3. **Limited scheduling flexibility**: Fixed 2-hour check interval; user cannot customize "only mornings"
4. **Single notification per cycle**: Even with high priority, only one notification delivered per worker wake-up

### Planned Enhancements

1. **User-configurable check intervals** (low priority) — allow power users to reduce to 1 hour or extend to 4
2. **Time-of-day preferences** (medium priority) — "only send notifications during 9 AM–6 PM"
3. **Multiple daily slots** (depends on research) — if users want morning/evening distinct slots
4. **Hybrid WorkManager+AlarmManager** (contingency) — if drift complaints accumulate

---

## 8. Verification Commands

To verify scheduling behavior in development:

```bash
# Check WorkManager state
adb shell dumpsys job com.retarget.app | grep retarget_notification_delivery

# View logs
adb logcat | grep -E "NotificationSchedulerManager|NotificationDeliveryWorker"

# Force immediate delivery (for testing)
adb shell am broadcast -a RETARGET_FORCE_DELIVERY

# Clear scheduler state
adb shell pm clear com.retarget.app
```

---

## 9. References

- PHASE2-CAMPAIGN.md section 3.3 — Original milestone specification
- docs/research/interruption-timing.md — Research basis for timing tolerances
- AGENTS.md §5.1.1 — Decision classification (WorkManager vs AlarmManager logged decision)
- WorkManager documentation: https://developer.android.com/topic/libraries/architecture/workmanager

---

*End of document*
