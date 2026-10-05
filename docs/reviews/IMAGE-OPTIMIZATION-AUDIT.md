# Image Optimization Audit: Creative Pack Assets

**Date:** 2026-01-XX  
**Per:** Phase 2 Campaign Plan Risk 2 — Notification Image Loading Performance  
**Scope:** Bundled creative pack assets in `app/src/main/assets/creative-packs/`

---

## Summary

| Metric | Value | Threshold | Status |
|--------|-------|-----------|--------|
| Total assets | 121 JPG files | — | — |
| Combined size | 67 MB | — | — |
| Images > 500KB | **All** (121/121) | 500KB | ⚠️ **FLAGGED** |
| Largest file | ~1.28 MB | 500KB | ⚠️ **FLAGGED** |
| Full-res heap cost (est.) | ~48 MB | Varies | ⚠️ **HIGH RISK** |
| Resize/downscale code | None found | Expected | ❌ **MISSING** |

---

## Per-Pack Size Table

### fresh_air Pack (37 MB total)

| Rank | File | Size | Est. Dimensions | Heap Cost (full decode) |
|------|------|------|-----------------|------------------------|
| 1 | noor-ullah-jan-Ty1Stt6lWTA-unsplash.jpg | 1,276 KB | ~4000×3000 | ~48 MB |
| 2 | sergei-shershen-D3h5lPieQmI-unsplash.jpg | 1,252 KB | ~4000×3000 | ~48 MB |
| 3 | xavier-von-erlach-CJu7BB6YxYc-unsplash.jpg | 1,192 KB | ~4000×3000 | ~48 MB |
| 4 | gayatri-malhotra-P9gkfbaxMTU-unsplash.jpg | 1,105 KB | ~4000×3000 | ~48 MB |
| 5 | aiman-ahmed-YKEhDcHJ9QI-unsplash.jpg | 1,095 KB | ~4000×3000 | ~48 MB |
| … | (remaining 55 files) | 700–1000 KB avg | ~4000×3000 | ~48 MB each |

### fruit Pack (30 MB total)

| Rank | File | Size | Est. Dimensions | Heap Cost (full decode) |
|------|------|------|-----------------|------------------------|
| 1 | noor-ullah-jan-Ty1Stt6lWTA-unsplash.jpg | 1,276 KB | ~4000×3000 | ~48 MB |
| 2 | yulia-shinova-2gKapCYKogo-unsplash.jpg | 966 KB | ~4000×3000 | ~48 MB |
| 3 | anton-shakirov-qMvmdK_rAvE-unsplash.jpg | 894 KB | ~4000×3000 | ~48 MB |
| 4 | snappr-0woN_qoISYQ-unsplash.jpg | 891 KB | ~4000×3000 | ~48 MB |
| 5 | adam-boukhris-JaSnfg04Hiw-unsplash.jpg | 846 KB | ~4000×3000 | ~48 MB |
| … | (remaining 58 files) | 600–800 KB avg | ~4000×3000 | ~48 MB each |

---

## Heap Cost Analysis

**Assumptions:**
- Typical Unsplash stock photos: 4000×3000 pixels (12 MP)
- Android `Bitmap` storage: ARGB_8888 = 4 bytes per pixel
- Full decode without downsampling

**Calculation:**
```
Heap = width × height × 4 bytes
     = 4000 × 3000 × 4
     = 48,000,000 bytes ≈ 48 MB per bitmap
```

**Risk scenario (Phase 2 notifications):**
- If notification delivery loads a full-size bitmap for BigPictureStyle:
  - Concurrent allocations during rotation could trigger OOM.
  - Large bitmaps also increase GC pressure during frequent swaps.
- Current code (`WallpaperService.kt`, lines 44, 56):
  ```kotlin
  BitmapFactory.decodeFile(imageFile.absolutePath)
  ```
  — **No `inSampleSize` applied; loads full resolution.**

---

## Resize/Downscale Pipeline Check

**Findings:**
- `grep -r "inSampleSize"` → **No matches**
- `grep -r "createScaledBitmap"` → **No matches**
- `grep -r "decodeFile"` → Only in `WallpaperService.kt`, both calls use default options (full decode).

**Conclusion:** No image downsizing occurs anywhere in the current pipeline. All assets are loaded at native resolution.

---

## Recommendations

### Critical Fix Required (Risk 2 Mitigation)

**Problem:** Phase 2 notifications will load 48 MB bitmaps per delivery, risking OOM and sluggish UI.

**Options:**

| Option | Description | Effort | Blast Radius | Risk if Wrong |
|--------|-------------|--------|--------------|---------------|
| A. Pre-resize during ingest | Add a build step that creates scaled variants (~800×600 for notifications, ~1200×800 for wallpaper) stored alongside originals. | Medium (build script + asset management) | Asset pipeline only | Storage bloat if too aggressive; still OOM if underscaled |
| B. Lazy downsampling in service | Modify `WallpaperService` and future `NotificationChannel` to decode with `inSampleSize` calculated from target viewport. | Low (~20 lines per channel) | Runtime behavior only | Miscalculated sampling causes blurry or still-large bitmaps |
| C. Hybrid (recommended) | Pre-create optimized notification variants (BigPictureStyle typically uses ~600–800px height); use in-sample decoding for wallpaper as fallback. | Medium-high | Both asset + runtime | Minimal if tested across devices |

**Recommendation:** **Option C** — pre-resize notification variants during asset ingestion (Phase 1 already bundled these packs, so this is retroactive). Justification:
1. BigPictureStyle notifications have a constrained viewport (~600–800px height max visible area).
2. Pre-resized assets eliminate runtime allocation spikes entirely.
3. Original high-res assets preserved for wallpaper use (if user prefers).

**Estimated sizing for notification variant:**
- Target height: 800 px (fits most screens at ~30% scale)
- Aspect ratio preserved: e.g., 4000×3000 → 1067×800
- Heap cost: 1067 × 800 × 4 ≈ 3.4 MB (↓93% from 48 MB)

### Implementation Tasks

1. **Add asset optimization script** (`scripts/optimize-assets.sh` or Kotlin Gradle task):
   - Input: `app/src/main/assets/creative-packs/*/`
   - Output: `app/src/main/assets/creative-packs/*/optimized/notifications/` and `/wallpapers/`
   - Tooling: `ffmpeg`, `ImageMagick`, or `libvips` (cross-platform).
   - Parameters:
     - Notifications: max height 800 px, JPEG quality 85%.
     - Wallpapers: max width 1920 px (or device-specific).

2. **Update `WallpaperService`** (deferred but recommended):
   ```kotlin
   val options = BitmapFactory.Options().apply { inSampleSize = calculateSampleSize(imageFile, maxWidth = 1920) }
   val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath, options)
   ```

3. **Modify `NotificationChannel`** (when implemented in Milestone 2.1):
   - Load from `optimized/notifications/` variant path.
   - Fall back to full-res with `inSampleSize` if optimized version missing.

4. **Asset manifest tracking:**
   - Add `pack_manifest.json` per pack listing available resolutions.
   - Gracefully degrade if optimized variant missing (log warning, use original).

---

## Edge Cases to Test

| Scenario | Behavior | Verification |
|----------|----------|--------------|
| Optimized variant missing | Fallback to full-res + warn | Unit test: mock missing file path |
| Device with low memory (<2 GB RAM) | Aggressive downsampling required | Manual test on old device/emulator |
| Rotation during low-memory state | GC thrashing risk | Stress test: rapid creative cycling |
| DST/daylight savings time crossing | Not image-related | Covered by scheduler tests |

---

## Next Steps (Action Items)

- [ ] **Immediate:** Implement asset preprocessing script (pre-resize notification variants).
- [ ] **Short-term:** Update `WallpaperService` to use `inSampleSize` for full-res fallback.
- [ ] **Medium-term:** Add memory-pressure monitoring to dynamically adjust sampling.
- [ ] **Documentation:** Add note to `README.md` about asset size requirements for contributors.

---

## References

- Phase 2 Campaign Plan, Section 8, Risk 2: "Notification Image Loading Performance"
- Android docs: [Loading Large Bitmaps Efficiently](https://developer.android.com/topic/performance/graphics/load-bitmap)
- Current code: `WallpaperService.kt` lines 44, 56 (full decode without sampling)

---

*Audit complete. Pending maintainer approval for asset pipeline changes (Major change per AGENTS.md §5.2).*
