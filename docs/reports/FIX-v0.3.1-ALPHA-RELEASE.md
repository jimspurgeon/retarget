# v0.3.1-alpha Fix Release Report

## Crash Diagnosis & Resolution (2026-10-06)

### Problem
v0.3.0-alpha crashed on startup on Android 17 (Pixel 10, GrapheneOS) with:
```
java.lang.IllegalStateException: Cannot access database on the main thread
  at androidx.room.RoomDatabase.assertNotMainThread
  at com.retarget.creative.ExposureDao_Impl.exposuresTodayByChannel
```

Root cause: Three reactive flows in `DashboardViewModel.kt` performed blocking Room queries inside `.map {}` operators that executed on the main (Compose) dispatcher.

### Solution
Added `.flowOn(Dispatchers.IO)` to upstream of three affected flows:
- `wallpaperPacingSummary`
- `notificationPacingSummary`  
- `checkInRates`

Total change: 5 lines (plus imports). No behavioral changes, only threading relocation.

### Pipeline Execution

#### 1. Crash Reproduction (Manual)
- Physical device: Pixel 10, GrapheneOS, Android 17
- Original crash captured via logcat, PID killed immediately on launch
- Emulator (API 37) did NOT reproduce — timing-dependent race

#### 2. Orca Worker Dispatch (fix-dashboard-mainthread-crash)
- Worktree created, blank terminal closed per hygiene protocol
- Worker dispatched with lumo-lite model
- Applied fix, ran tests, committed: `5ca45fe`

#### 3. Artifact Build & On-Device Verification
- Built debug APK: 88MB
- Installed on physical device
- **Cold launch survived 35+ seconds**, zero crash entries in logcat

#### 4. Gatekeeper Review (lumo-max)
- Independent review approved
- Verified flowOn semantics correct
- Hunted for latent similar bugs — none found
- Posted findings on PR #18

#### 5. QA Stage (lumo-lite)
- Verified artifact identity via aapt2 badging
- Re-tested on-device, confirmed no main-thread DB violations
- Posted "QA VERDICT: approved" on PR #18

### Outcome
**PR #18 created** — ready for human merge. Fix eliminates the alpha-release blocker.

---

## Generation Error Investigation (Transient)

During debugging, encountered recurring "Error: An error occurred during generation" — traced to transient `lumo` provider connection failures (network drops during large payload uploads). Mitigations:
- Reduce output verbosity
- Use filtered/grep'd logs
- Retry turns when it happens
- `/bug` reports to pi devs tracked in `~/.pi/agent/crashes.json`

Two crash records logged (both 2026-10-02) unrelated to this session; current session has zero generation errors since switching to Orca worker pipeline.

---

## Next Steps
1. Human merge of PR #18 (after final visual check)
2. Consider adding explicit regression test for main-thread DB violation detection
3. Begin Phase 3 M3.1 implementation (export-only data backup)
4. Update CHANGELOG with crash fix entry
