# Image Optimization Audit — Risk-2 (Phase 2 Notifications)

**Date:** 2025-10-04  
**Trigger:** PHASE2-CAMPAIGN.md Risk 2 ("Notification Image Loading Performance")  
**Classification:** Major change recommendation (impacts Phase 2 delivery timeline)  

---

## Executive Summary

- **What we did:** Measured all bundled creative pack image sizes, assessed BitmapFactory decode paths in `WallpaperService`, and evaluated heap costs for full-res vs. scaled decode.
- **What we found:** 72 of 117 images (61%) exceed the 500KB design doc mitigation threshold; some reach 1.2MB. Current code performs full-res decode without any downsampling (`inSampleSize` or `inJustDecodeBounds` not used). Heap estimates: a single 2160×1440 image at 4 bytes/pixel consumes ~12.5MB; stacked allocations risk OOM under memory pressure.
- **What's left:** A build-time resize pipeline must be implemented before Phase 2 notifications roll out. Below we recommend a tooling approach and provide concrete heap math.

---

## 1. Bundle Image Size Analysis

### Threshold Definition

Per AGENTS.md / design doc: **flag anything > 500KB**.

### Per-Pack Statistics

| Pack       | Total Images | >500KB Count | % Over Threshold | Max Size  | Avg Size (all) |
|------------|--------------|--------------|------------------|-----------|----------------|
| fresh_air  | 60           | 44           | 73%              | 1,223 KB  | ~620 KB        |
| fruit      | 57           | 28           | 49%              | 1,246 KB  | ~510 KB        |
| **Total**  | **117**      | **72**       | **61%**          | **1,246KB**| **~565 KB**    |

### Flagged Images (>500KB)

#### fresh_air pack (44 images)
- aiman-ahmed-YKEhDcHJ9QI-unsplash.jpg: 1,069KB
- andrew-phares-ck7Nc_5-u68-unsplash.jpg: 1,000KB
- gayatri-malhotra-P9gkfbaxMTU-unsplash.jpg: 1,079KB
- sergei-shershen-D3h5lPieQmI-unsplash.jpg: 1,223KB ⚠️
- xavier-von-erlach-CJu7BB6YxYc-unsplash.jpg: 1,164KB
- *(plus 39 others in range 501–894KB)*

#### fruit pack (28 images)
- noor-ullah-jan-Ty1Stt6lWTA-unsplash.jpg: 1,246KB ⚠️
- adam-boukhris-JaSnfg04Hiw-unsplash.jpg: 826KB
- anton-shakirov-qMvmdK_rAvE-unsplash.jpg: 873KB
- snappr-0woN_qoISYQ-unsplash.jpg: 870KB
- yulia-shinova-2gKapCYKogo-unsplash.jpg: 944KB
- *(plus 23 others in range 506–828KB)*

**Observation:** The ingestion pipeline currently bundles original Unsplash downloads without any compression/resizing step. Manifest metadata includes dimensions (e.g., 2160×1440), but these are never used to guide downsampling.

---

## 2. Decode Path Analysis

### Current Implementation (`WallpaperService.kt`)

```kotlin
// Line 49: Full-res decode, no sampling
val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)

// Line 72: Same pattern for restore snapshot
val bitmap = BitmapFactory.decodeFile(snapshot.absolutePath)
```

**Assessment:** Both calls use `BitmapFactory.decodeFile()` with default options—full resolution, immediate allocation.

### Missing Safeguards

- ❌ `inJustDecodeBounds` not used to inspect dimensions before allocating.
- ❌ `inSampleSize` not calculated based on target display size.
- ❌ No `inPurgeable` or memory-mapping optimization.
- ❌ No exception handling specifically for OOM during decode.

### Heap Cost Calculation

**Formula:** `heapBytes = width × height × 4` (ARGB_8888, Android default)

| Resolution   | Pixels     | Heap (MB) | Typical Use Case           |
|--------------|------------|-----------|----------------------------|
| 2160×1440    | 3.11M      | 12.4 MB   | Wallpaper (portrait/landscape) |
| 1440×2160    | 3.11M      | 12.4 MB   | Wallpaper (reverse)        |
| 1080×1920    | 2.07M      | 8.3 MB    | Phone screen               |
| 800×600      | 0.48M      | 1.9 MB    | BigPicture notification    |
| 400×300      | 0.12M      | 0.5 MB    | Thumbnail/preview          |

**Risk Scenario:**
- Wallpaper swap decodes 12.4MB.
- If the app previously held another large bitmap (e.g., cached preview), total transient allocation could exceed 20–25MB.
- On low-end devices with tight heap limits (e.g., 48MB total app heap), this risks OOM kill.

### Notification-Specific Risk (Phase 2)

`BigPictureStyleNotification` renders at a smaller viewport (~800px wide max). Full-res decode is wasteful:

- **Current path:** 12.4MB decoded → scaled down by system for notification → extra GC pressure.
- **Optimized path:** Pre-scale to 800×450 → 1.4MB heap → faster rendering, lower latency.

---

## 3. Ingestion/Loader Assessment

### Current Flow (`PersistentCreativeRepository.ensureLoaded()`)

1. Reads `manifest.json` from assets.
2. Validates fields via `CreativeManifestLoader`.
3. Inserts `CreativeEntity` rows with `imagePath` pointing to original asset file.
4. **No resizing occurs**—original high-res JPEGs are shipped as-is.

**Gap:** The ingestion pipeline should either:
- Resize during build-time packaging (preferred), OR
- Resize on-first-load and cache scaled versions (fallback, adds runtime cost).

**Recommendation:** Implement build-time resizing so the APK bundles optimized images from day one.

---

## 4. Recommendations

### 4.1 Immediate Mitigation (Phase 2 Blocker)

**Action:** Add a build-time image optimization step that downscales all bundled creatives to a max dimension of **1080px** (maintaining aspect ratio) and compresses to **≤500KB**.

**Rationale:**
- 1080px max dimension covers 99th percentile screen widths.
- Notification BigPicture viewport (~800px) is well served.
- Compression to ≤500KB aligns with existing design doc thresholds.

### 4.2 Tooling Approach Options

#### Option A: Python Script (Recommended)
**Mechanism:** Extend existing `scripts/fetch_creatives.py` to call `Pillow` for resizing/compression.

**Pros:**
- Reuses current ingestion pipeline.
- Runs offline; no external service dependency.
- Can be integrated into Gradle pre-build task.

**Cons:**
- Requires Python + Pillow installed locally or in CI.
- Need to handle incremental updates (skip already-optimized images).

**Pseudocode:**
```python
from PIL import Image
import os

def optimize_image(input_path, output_path, max_dim=1080, quality=85):
    with Image.open(input_path) as img:
        # Downscale if needed
        if max(img.size) > max_dim:
            img.thumbnail((max_dim, max_dim), Image.LANCZOS)
        # Save with progressive JPEG
        img.save(output_path, "JPEG", quality=quality, optimize=True, progressive=True)
```

**Estimated Effort:** 1–2 days (script + Gradle integration + CI update).

#### Option B: Build-Time Gradle Task
**Mechanism:** Custom Gradle task invoking system `jpegtran` or `mozjpeg` CLI tools.

**Pros:**
- Tighter integration with build graph.
- No Python dependency.

**Cons:**
- Requires native toolchain setup per dev machine.
- Platform-specific binaries (Windows/Mac/Linux).

**Recommendation:** Start with Option A (Python + Pillow); migrate to Option B if Python becomes a friction point.

### 4.3 Runtime Safety Net (Future)

Even with build-time optimization, add defensive decoding in `WallpaperService`:

```kotlin
fun decodeWithSampling(imageFile: File, maxDim: Int = 1080): Bitmap? {
    // First pass: get bounds
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(imageFile.absolutePath, boundsOpts)
    
    // Calculate sample size
    val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, maxDim)
    
    // Second pass: decode with sampling
    BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }.let { BitmapFactory.decodeFile(imageFile.absolutePath, it) }
}

fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
    var sampleSize = 1
    while (width / sampleSize > maxDim || height / sampleSize > maxDim) {
        sampleSize *= 2
    }
    return sampleSize
}
```

**Benefit:** Protects against future regressions if larger images slip into the bundle.

### 4.4 Manifest Metadata Enhancement

Update `CreativeManifestLoader` to store **optimized dimensions** (post-resize) alongside original dimensions. This allows the UI to display accurate aspect ratios without decoding the image.

---

## 5. Verification Plan

Post-implementation checks:

1. **Size Audit:** Re-run `ls -la` script; verify zero images >500KB.
2. **Build Integration:** Confirm resize task runs during `assembleDebug`.
3. **Runtime Test:** Manually decode largest image; confirm heap <4MB.
4. **Visual Regression:** Compare pre/post images side-by-side; ensure no perceptible degradation.

---

## 6. Risks & Open Questions

| Risk                          | Impact | Mitigation                          |
|-------------------------------|--------|-------------------------------------|
| Resized images look degraded  | Medium | Tune JPEG quality; A/B test with users |
| Pipeline adds build time      | Low    | Incremental builds; cache optimized outputs |
| Existing APKs have large imgs | High   | This is a forward-looking fix; old builds remain as-is |

**Open Question:** Should we also generate thumbnail variants (200×150) for dashboard previews?

- **Option A:** Yes, reduce main thread decode overhead.
- **Option B:** No, lazy-load and scale on-demand.
- **Recommendation:** Defer to Phase 3 (dashboard polish); Phase 2 focus is notification delivery.

---

## 7. Conclusion

**Verdict:** Phase 2 notification rollout **should not proceed** until the build-time resize pipeline is implemented. The current 61% over-threshold rate poses measurable OOM risk and wastes bandwidth/storage.

**Next Steps:**
1. ✅ Create issue for "Add image optimization build step".
2. ⏳ Assign to sprint; estimate 2–3 days including CI integration.
3. ⏳ After completion, re-audit and document before enabling notifications.

---

*Appendix: Full image size listings available in terminal output and raw ls results.*

---

*Generated by automated audit per AGENTS.md §5.3 change report protocol.*
