<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Retarget — turning Advertising's own toolbox toward your goals.
Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
-->

# User Testing Script — Retarget App

**Version:** 1.0  
**Date:** 2026-01-XX  
**Build:** Debug APK from `integrate/phase2-notifications` branch  
**Purpose:** Validate end-to-end user experience for notification-based nudge delivery, onboarding flows, and ethical control mechanisms.

---

## Pre-Test Setup

### Environment Requirements

| Requirement | Value | Notes |
|-------------|-------|-------|
| Android SDK | 34+ | Target SDK, minSdk 26 |
| Java | 17 | `sdk use java 17.0.20-amzn` |
| Gradle | 9.8+ | Wrapper included in repo |
| Emulator/Device | API 30+ recommended | Physical device preferred for notification testing |
| Permissions | POST_NOTIFICATIONS (Android 13+) | Must be opt-in, not default-granted |

### Build & Install Commands

```bash
# Navigate to project root
cd C:/Users/Jim/orca/workspaces/advert-app/usertesting-prep

# Set Java version
source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk use java 17.0.20-amzn

# Build debug APK
./gradlew assembleDebug

# Verify APK size (expected ~85MB due to creative asset images)
# Note: Production builds would use on-demand image loading to reduce size
ls -lh app/build/outputs/apk/debug/app-debug.apk

# Install on connected device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Or clean install
adb uninstall com.retarget.advertapp 2>/dev/null
adb install app/build/outputs/apk/debug/app-debug.apk
```

**APK Size Note:** The debug APK is approximately 85MB due to bundled creative asset images (fresh_air: 37MB, fruit: 30MB). This is acceptable for user testing but would be optimized in production via on-demand asset loading or download.

### Factory Reset Procedure (Test Device)

Before starting a new test session:

```bash
# Clear app data (one-click reset)
adb shell pm clear com.retarget.advertapp

# Verify app is in fresh state
adb shell dumpsys package com.retarget.advertapp | grep "firstInstallTime"

# Optional: Full emulator wipe (cold boot from scratch)
emulator -avd <your_avd_name> -wipe-data
```

**Manual Steps (if ADB unavailable):**
1. Open Settings → Apps → Retarget
2. Tap "Storage & cache" → "Clear storage" / "Clear data"
3. Force stop the app
4. Relaunch — should show onboarding screen

---

## Test Session Script

### Step 1: Installation & First Launch

**Expected Behavior:**
- ✅ APK installs without errors
- ✅ App launches to onboarding screen (not dashboard)
- ✅ No crash on cold start
- ✅ No notification permission dialog appears yet (opt-in only)

**Actions:**
1. Install APK via ADB or manual sideload
2. Launch app from home screen
3. Observe first screen

**Pass Criteria:**
- [ ] Onboarding screen displays with title "Pick your first campaign"
- [ ] Subtitle reads: "Curated ad campaigns for wellbeing goals..."
- [ ] "Activate" button visible
- [ ] "Not now" skip option available

---

### Step 2: Onboarding — Preset Selection

**Expected Behavior:**
- ✅ Shows 4 preset campaign cards (Hydration, Fresh Air, More Fruit, More Vegetables)
- ✅ Each card has descriptive blurb
- ✅ User can select multiple presets
- ✅ Selection persists to database

**Actions:**
1. Read onboarding copy
2. Tap "Hydration" preset card (should highlight)
3. Tap "Fresh Air" preset card (multi-select)
4. Tap "Activate" button

**Pass Criteria:**
- [ ] Selected presets visually distinguished (highlight/border)
- [ ] At least one preset must be selected (activation disabled if none)
- [ ] After activation, transitions to goal detail screen

---

### Step 3: Goal Activation & Configuration

**Expected Behavior:**
- ✅ Goal created with selected preset creative pack
- ✅ Dashboard shows newly activated goal
- ✅ Channel controls visible (Wallpaper, Notifications toggles)
- ✅ Notification toggle OFF by default (ethical requirement)

**Actions:**
1. On goal detail screen, observe default settings
2. Note channel toggle states
3. Navigate to dashboard (usually automatic after activation)

**Pass Criteria:**
- [ ] Dashboard shows goal name (e.g., "Hydration")
- [ ] Wallpaper channel enabled by default (Phase 1 baseline)
- [ ] Notification channel DISABLED by default (Phase 2 ethical guardrail)
- [ ] Pacing display shows "0/X today" for each channel
- [ ] Settings FAB (⚙️) accessible

---

### Step 4: Enable Notifications (Opt-In Flow)

**Expected Behavior:**
- ✅ User explicitly enables notification nudges
- ✅ Android permission dialog appears (Android 13+)
- ✅ Notification schedule respects quiet hours (22:00–07:00)

**Actions:**
1. Tap "Notifications" toggle ON
2. If Android 13+, observe system permission dialog
3. Grant permission
4. Observe UI feedback

**Pass Criteria:**
- [ ] System permission dialog appears (Android 13+) or skipped (<13)
- [ ] Toggle switches to ON state
- [ ] Toast or inline message confirms "Notification nudges enabled"
- [ ] No notification fired immediately (respects scheduling policy)

**Optional Negative Test:**
- Deny permission when prompted
- Toggle should revert to OFF state
- Tooltip explains why notifications won't work

---

### Step 5: Wait for Nudge Delivery

**Expected Behavior:**
- ✅ Scheduler computes next slot based on BudgetPolicy
- ✅ Quiet hours block delivery (if within 22:00–07:00)
- ✅ Fresh-start boosts applied (Monday mornings, etc.)
- ⏳ Notification arrives at computed time (may drift ±30 min under Doze)

**Actions:**
1. Note current time
2. Check dashboard pacing counter
3. Wait for notification (can expedite by:
   - Disabling battery optimization for app
   - Setting test goal with immediate slot)

**Known Limitations:**
- **WorkManager precision:** Under Doze mode, scheduled slots may drift up to ±30–45 minutes
- **Quiet hours enforcement:** Hard block — no delivery between 22:00–07:00 unless user changes setting
- **First delivery delay:** May take up to 5 minutes for WorkManager to initialize scheduler

**Pass Criteria:**
- [ ] Notification appears with BigPictureStyle (image + copy + actions)
- [ ] Image matches selected creative pack (e.g., nature imagery for Fresh Air)
- [ ] Copy line is relevant to goal (hydration reminder, fruit suggestion)
- [ ] Notification ID is unique (not replacing previous)

---

### Step 6: Observe Nudge — Transparency Check

**Expected Behavior:**
- ✅ Notification content reflects creative fatigue-aware rotation
- ✅ "Why am I seeing this?" explanation accessible
- ✅ Cross-channel crowding backoff respected (if wallpaper also enabled)

**Actions:**
1. Examine notification content
2. Tap notification (opens app or transparency screen)
3. Locate "Why am I seeing this?" info (usually ⓘ icon or long-press)

**Pass Criteria:**
- [ ] Explanation visible: goal name, timing rationale (e.g., "morning routine time")
- [ ] Budget status shown (e.g., "3/3 notifications today")
- [ ] Plain-language framing ("scheduled at your peak responsive time")
- [ ] No technical jargon (multipliers, numeric scores hidden)

---

### Step 7: Notification Actions — Check-In, Snooze, Volume Down

#### 7a: Check-In Action

**Expected Behavior:**
- ✅ Tapping "Check in" records success event
- ✅ Dashboard updates check-in count
- ✅ Exposure→conversion ratio visible

**Actions:**
1. Tap "Check in" button on notification
2. Return to dashboard
3. Observe check-in metrics

**Pass Criteria:**
- [ ] Check-in counter increments (visible on dashboard)
- [ ] Toast confirmation ("Thanks for checking in!")
- [ ] Exposure log updated (database round-trip verified)

#### 7b: Snooze Action

**Expected Behavior:**
- ✅ Snoozing for 2h respects cooldown policy
- ✅ No duplicate notification during snooze window
- ✅ Scheduler recalculates next slot

**Actions:**
1. Tap "Snooze 2h" on notification
2. Notification dismissed
3. Wait <2h (observe no re-fire)

**Pass Criteria:**
- [ ] No notification reappears within 2-hour snooze window
- [ ] After 2h, next slot computed according to BudgetPolicy

#### 7c: "Fewer Like This" Action

**Expected Behavior:**
- ✅ One-tap volume reduction decreases daily cap
- ✅ Floor enforced (min 0/day)
- ✅ UI feedback confirms reduction

**Actions:**
1. Tap "Fewer like this" action
2. Observe toast/snackbar
3. Navigate to Settings to verify cap decrease

**Pass Criteria:**
- [ ] Toast: "Reduced to X/day. Change in Settings anytime."
- [ ] Settings screen shows decreased target (e.g., 3/day → 2/day)
- [ ] Easy to increase again (no dark pattern blocking reversal)

---

### Step 8: Opt-Out Flow — Channel Disable

**Expected Behavior:**
- ✅ One-tap disable stops all future notifications for goal
- ✅ WorkManager jobs cancelled immediately
- ✅ Re-enable path as easy as disable

**Actions:**
1. Navigate to Settings (FAB on dashboard)
2. Locate notification channel control
3. Toggle OFF
4. Observe immediate feedback

**Pass Criteria:**
- [ ] Toggle switches to OFF state
- [ ] Toast: "Notification nudges paused. Tap to re-enable."
- [ ] WorkManager jobs cancelled (verify via `adb shell dumpsys job_scheduler | grep retarget`)
- [ ] No residual scheduled notifications

**Negative Test:**
- Re-enable notifications
- System should recompute slots and queue new WorkManager jobs

---

### Step 9: Transparency Screen Deep Dive

**Expected Behavior:**
- ✅ Technical details available on demand (not forced on user)
- ✅ Ethical framing maintained (user control emphasized)
- ✅ Link to settings prominent

**Actions:**
1. From dashboard or notification tap, access "Why am I seeing this?"
2. Explore full explanation screen
3. Look for "More details" link (if available)

**Pass Criteria:**
- [ ] Human-readable summary visible upfront
- [ ] "More details" reveals technical info (multipliers, policy rules)
- [ ] Settings link allows immediate adjustment
- [ ] Language emphasizes self-direction ("your choice", "your control")

---

### Step 10: Delete Goal Flow

**Expected Behavior:**
- ✅ Goal deletion cascades to cancel all scheduled work
- ✅ Ledger exposures retained (anonymized analytics value)
- ✅ Dashboard removes goal from active list

**Actions:**
1. Navigate to goal detail screen
2. Tap "Delete this goal"
3. Confirm deletion dialog
4. Observe dashboard update

**Pass Criteria:**
- [ ] Confirmation dialog warns of cancellation (no confirm-shaming)
- [ ] Goal removed from dashboard
- [ ] Associated WorkManager jobs cancelled
- [ ] Can recreate goal from preset (no permanent lockout)

---

### Step 11: Dashboard Pacing Audit

**Expected Behavior:**
- ✅ Accurate counts per channel (exposures vs. caps)
- ✅ Visual indicators when approaching limits
- ✅ Cross-channel aggregation visible

**Actions:**
1. Observe dashboard pacing displays
2. Compare against actual notification count sent
3. Check for visual warnings near cap

**Pass Criteria:**
- [ ] Pacing format: "Channel: X/Y today"
- [ ] Visual feedback when near cap (e.g., color change at 80%)
- [ ] Zero-padding shown for channels with no sends ("0/3 today")

---

## Known Limitations & Bug Log

### Current Version Limitations

| Issue | Impact | Workaround | Status |
|-------|--------|------------|--------|
| WorkManager drift under Doze | Notifications may arrive ±30–45 min off schedule | Disable battery optimization for test | Acknowledged |
| Single notification channel | All goals share one system channel | Accept grouping behavior; per-goal notification IDs | Design decision |
| Round-robin copy selection | No fatigue-weighted copy variation yet | — | Planned post-MVP |
| No hybrid AlarmManager fallback | Precision timing not guaranteed for critical moments | Use WorkManager only for MVP | Logged |

### Known Bugs (As of Build Date)

- **Bug #XXX:** Notification not dismissing on snooze action (workaround: manually swipe dismiss)
- **Bug #YYY:** Dashboard pacing count lags by 1 refresh cycle (workaround: pull-to-refresh)

---

## Test Data Sanitization Checklist

Before sharing test results or screenshots:

- [ ] No real device IDs visible in logs/screenshots
- [ ] No personal goal names (use preset campaigns only)
- [ ] Timestamps anonymized (replace "Mon Jan 12 08:30" with "T+0 min")
- [ ] No email addresses or phone numbers in crash reports
- [ ] Device hostname generic ("test-device" not "Jim's Pixel 6")

**Synthetic Data Standards:**
- Goal names: Use presets (Hydration, Fresh Air, More Fruit, More Vegetables)
- User identifiers: Synthetic UUIDs only (generated by Room on insert)
- Timestamps: Relative offsets ("+5 min", "+2h") rather than absolute times

---

## Post-Session Data Collection

### Metrics to Capture

1. **Time-to-first-notification:** From goal activation to first nudge
2. **Dismissal rate:** How many notifications dismissed without interaction
3. **Check-in conversion rate:** Taps on "Check in" ÷ total exposures
4. **Volume reduction usage:** Count of "Fewer like this" taps
5. **Re-enable rate:** Users who disable then re-enable notifications

### Logs to Export

```bash
# Crash logs (if any)
adb logcat -d | grep -i "retarget\|FATAL" > crash_log.txt

# Notification history
adb shell dumpsys notification | grep -A 20 "com.retarget.advertapp" > notification_history.txt

# Job scheduler state
adb shell dumpsys job_scheduler | grep retarget > scheduler_state.txt

# Database export (anonymized)
adb pull /data/data/com.retarget.advertapp/databases/goal.db ./goal_db_dump.db
# Then run: sqlite3 goal_db_dump.db ".dump" > schema_and_data.sql
# Anonymize any PII before sharing
```

### Privacy Compliance

- All database exports must be scrubbed of timestamps linking to real-world events
- Exposure logs retain only creative IDs and channel types (no user identity)
- Check-in notes field (if present) should be blank or synthetic ("test note 1")

---

## Emergency Stop Procedures

If app exhibits unexpected behavior:

1. **Kill all scheduled work:**
   ```bash
   adb shell cmd job --disable com.retarget.advertapp
   ```

2. **Uninstall cleanly:**
   ```bash
   adb uninstall com.retarget.advertapp
   ```

3. **Rotate any leaked credentials (dev-only):**
   - If secrets.properties accidentally committed, rotate Unsplash API key
   - Regenerate signing keystore if debug keystore exposed

---

## Feedback Submission

After completing test session, document:

1. **Overall satisfaction:** 1–5 scale
2. **Most confusing UI element:** (free text)
3. **Ethical concerns:** Any dark patterns or manipulative UX observed
4. **Blocking bugs:** Crashes, deadlocks, data loss
5. **Feature requests:** Missing functionality for production launch

Submit findings to maintainer via issue tracker or direct communication.

---

*End of User Testing Script v1.0*
