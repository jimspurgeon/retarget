# Hotfix: Hydration Content Gap (Wallpaper "Nothing Happens")

## Symptom (reported 2026-10-06)
User enabled wallpaper on a hydration goal but observed no wallpaper changes, despite the scheduler appearing active in logcat.

## Root Cause
**Missing content**, not a code defect. The app bundles creative packs per-gotheme:
- `fresh_air` → NATURE_TIME
- `fruit` → PLANT_BASED_WHOLE_FOODS  
- `vegetables` → PLANT_BASED_WHOLE_FOODS

**No hydration pack existed.** When the user activated the `hydration` preset (HYDRATION theme), `WallpaperRotationWorker` correctly found zero matching creatives and logged:
```
No bundled creatives for active goals; skipping
```

The code path was correct — it's a **content gap** in the v0.3.0 release.

## Fix Implemented (2026-10-06)
Ran the existing creative pipeline to fill the gap:
```bash
python scripts/fetch_creatives.py --theme hydration
```

Results:
- **60 images** fetched from Unsplash API, organized into 6 sub-themes
- Sub-themes: sparkling water pour, mountain stream, ocean wave, morning dew, water ripple, citrus-infused
- All images: 1440px long-edge, recompressed @ q78, validated for attribution & license
- Ledger updated (no duplicates, never-ship-twice rule enforced)

Files added:
- `app/src/main/assets/creative-packs/hydration/` (60 JPGs + manifest.json)
- `.creative-ledger.json` updated

Commit: `feat(creative): bundle hydration creative pack (60 images)`

## Verification Plan
1. Rebuild debug APK with new pack (done)
2. Install on device
3. Complete onboarding, select "Hydration" preset, ensure wallpaper enabled
4. Within quiet hours (7 AM–10 PM), expect wallpaper rotation within ~minutes (worker runs every 8h, initialDelay=0 on schedule)

Known behavior:
- Quiet hours enforcement: worker skips 22:00–07:00 (as designed)
- Rotation cadence: ~8 hours (WorkManager periodic minimum)
- Exposure budget: max 4/day (per `CampaignSettings.MAX_WALLPAPER_PER_DAY`)

## Next Release
Tag as `v0.3.1` (or `v0.3.1-hotfix`) with:
- Startup crash fix (already shipped)
- Hydration content (this hotfix)
- Changelog entry noting the gap closure

## Prevention
Add CI validation to catch missing-theme gaps:
```yaml
- name: Validate creative coverage
  run: |
    python scripts/fetch_creatives.py --validate
    # Future: assert all catalog presets have ≥N images
```
