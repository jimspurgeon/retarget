#!/usr/bin/env python3
"""Dev-side creative pipeline for retarget.

Fetches top Unsplash images per preset theme, recompresses them to
wallpaper-appropriate sizes, and writes per-pack manifests with mandatory
license/photographer metadata. The resulting assets are committed to the
repo and bundled into the APK; the app itself is fully offline.

Guarantees (issue #4):
  - No image ships twice, ever: a shipped-IDs ledger (.creative-ledger.json)
    persists across runs; images already shipped are skipped. Quarterly
    refreshes draw only from never-before-shipped photos.
  - Every image has photographer + license metadata or the pack fails
    validation before writing.

Usage:
    python scripts/fetch_creatives.py --theme fresh-air
    python scripts/fetch_creatives.py --all         # every catalog preset
    python scripts/fetch_creatives.py --validate     # validate packs only
    python scripts/fetch_creatives.py --handpicked fresh-air   # ingest hand-picked
                                                             # Unsplash downloads

Requires: secrets.properties with UNSPLASH_ACCESS_KEY (see template).
Network is used ONLY by this dev script, never by the app.
"""

import argparse
import hashlib
import json
import io
import os
import re
import shutil
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

# Windows consoles default to cp1252; photographer names are international.
# Force UTF-8 on stdout/stderr so a name like "João" can't crash the pipeline.
if sys.stdout.encoding and sys.stdout.encoding.lower() not in ("utf-8", "utf8"):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS_DIR = os.path.join(REPO_ROOT, "app", "src", "main", "assets", "creative-packs")
LEDGER_PATH = os.path.join(REPO_ROOT, ".creative-ledger.json")
SECRETS_PATH = os.path.join(REPO_ROOT, "secrets.properties")

API_BASE = "https://api.unsplash.com/search/photos"
PHOTO_PAGE = "https://api.unsplash.com/photos/"

# Quality bar: long edge >= 1600 px source minimum, re-encoded to 1440 max.
MIN_SOURCE_LONG_EDGE = 1600
TARGET_LONG_EDGE = 1440
JPEG_QUALITY = 78

# Hand-picked mode: images downloaded manually from unsplash.com are kept
# near-original. 2160 px long edge exceeds every shipping phone display
# (4K-phone wallpapers are 2160p), q85 JPEG is visually lossless at that
# size, and ~1-2 MB/image keeps the APK defensible (user decision, Oct 2026).
HANDPICKED_LONG_EDGE = 2160
HANDPICKED_JPEG_QUALITY = 85

# Unsplash search params: rank by relevance, one page at a time.
PER_PAGE = 30


def load_secrets():
    if not os.path.exists(SECRETS_PATH):
        sys.exit("ERROR: secrets.properties not found. Copy secrets.properties.template.")
    key = None
    with open(SECRETS_PATH, encoding="utf-8") as fh:
        for line in fh:
            if line.startswith("UNSPLASH_ACCESS_KEY="):
                key = line.split("=", 1)[1].strip()
    if not key:
        sys.exit("ERROR: UNSPLASH_ACCESS_KEY missing/empty in secrets.properties.")
    return key


def load_ledger():
    """shipped photo IDs -> pack/theme, persisted forever so nothing ships twice."""
    if os.path.exists(LEDGER_PATH):
        with open(LEDGER_PATH, encoding="utf-8") as fh:
            return json.load(fh)
    return {}


def save_ledger(ledger):
    with open(LEDGER_PATH, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(ledger, fh, indent=2, sort_keys=True)
        fh.write("\n")


def api_get(url, key, params=None):
    if params:
        url = url + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"Authorization": f"Client-ID {key}"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def search_photos(key, query, page):
    data = api_get(API_BASE, key, {
        "query": query,
        "per_page": PER_PAGE,
        "page": page,
        "orientation": "portrait",  # wallpaper-first
        "content_filter": "high",
    })
    return data.get("results", [])


def download_file(url, dest):
    req = urllib.request.Request(url, headers={"User-Agent": "retarget-dev-pipeline"})
    with urllib.request.urlopen(req, timeout=60) as resp, open(dest, "wb") as out:
        shutil.copyfileobj(resp, out)


def longest_edge(path):
    """Return (width, height) from a JPEG's SOF markers without Pillow."""
    with open(path, "rb") as fh:
        data = fh.read()
    i = 2
    while i < len(data):
        if data[i] != 0xFF:
            i += 1
            continue
        marker = data[i + 1]
        if marker in (0xC0, 0xC1, 0xC2, 0xC3):  # SOF0/1/2/3
            h = (data[i + 5] << 8) | data[i + 6]
            w = (data[i + 7] << 8) | data[i + 8]
            return w, h
        if marker in (0xD8, 0x01) or 0xD0 <= marker <= 0xD7:
            i += 2
            continue
        seg_len = (data[i + 2] << 8) | data[i + 3]
        i += 2 + seg_len
    raise ValueError(f"No SOF marker found in {path}")


def recompress(src, dest, max_edge=TARGET_LONG_EDGE, quality=JPEG_QUALITY):
    """Resize to max_edge and re-encode. Uses Pillow when available;
    falls back to ffmpeg; refuses to ship un-resized images."""
    try:
        from PIL import Image  # noqa: PLC0415
        with Image.open(src) as img:
            img = img.convert("RGB")
            w, h = img.size
            scale = max_edge / max(w, h)
            if scale < 1:
                img = img.resize((round(w * scale), round(h * scale)), Image.LANCZOS)
            img.save(dest, "JPEG", quality=quality, optimize=True)
        return True
    except ImportError:
        pass
    if shutil.which("ffmpeg"):
        subprocess.run([
            "ffmpeg", "-y", "-loglevel", "error", "-i", src,
            "-vf", f"scale='if(gt(iw,ih),min({TARGET_LONG_EDGE},iw),-2)':"
                   f"'if(gt(iw,ih),-2,min({TARGET_LONG_EDGE},ih))'",
            "-q:v", "4", dest,
        ], check=True)
        return True
    print("ERROR: neither Pillow nor ffmpeg available; cannot recompress. "
          "pip install Pillow and retry.", file=sys.stderr)
    return False


def slugify_theme(theme_id):
    return theme_id.replace("-", "_")


def parse_handpicked_candidates(filename):
    """Parse a manual Unsplash download filename like 'jane-doe-Ab12Cd34-unsplash.jpg'.

    Unsplash names downloads '<photographer-slug>-<photo-id>-unsplash.jpg', but the
    photo ID itself may contain dashes (even leading ones: 'nick-taylor--MpqygIXVzo'
    has ID '-MpqygIXVzo'), and the photographer slug may be empty or mimic an ID.
    No local heuristic can split these reliably, so return ALL right-anchored
    candidates, shortest first; the caller verifies against the API and accepts
    the first hit. Candidates under 6 chars are dropped (photo IDs are ~10-11).
    Returns a list of candidate photo IDs (possibly empty).
    """
    if not filename.endswith("-unsplash.jpg"):
        return []
    base = filename[: -len("-unsplash.jpg")]
    parts = base.split("-")
    cands = []
    for i in range(len(parts) - 1, 0, -1):
        cand = "-".join(parts[i:])
        if len(cand) >= 6 and any(ch.isalnum() for ch in cand):
            cands.append(cand)
    return cands


def handpicked_pack(theme_id, key, ledger, dry_run=False):
    """Ingest a directory of hand-picked Unsplash downloads (user decision,
    Oct 2026: manual curation beats search-term relevance for visual quality;
    see imagery-domains.md anti-pattern 3).

    Files arrive as full-resolution '<slug>-<id>-unsplash.jpg' downloads. For each:
      1. Generate right-anchored photo-ID candidates from the filename and accept
         the first that resolves via the API (resumable via a sidecar cache, with
         404s negatively cached, so an interrupted run doesn't re-burn the 50
         req/hr demo rate limit).
      2. Re-encode to HANDPICKED_LONG_EDGE @ HANDPICKED_JPEG_QUALITY in place.
      3. Append to the manifest and the never-reuse ledger.

    Existing images with the same filename are skipped, so the mode is safe to
    re-run after adding more photos to the directory.
    """
    pack_dir = os.path.join(ASSETS_DIR, slugify_theme(theme_id))
    os.makedirs(pack_dir, exist_ok=True)
    manifest_path = os.path.join(pack_dir, "manifest.json")
    cache_path = os.path.join(pack_dir, ".handpicked-meta-cache.json")

    if os.path.isfile(manifest_path):
        with open(manifest_path, encoding="utf-8") as fh:
            manifest = json.load(fh)
    else:
        manifest = {"packId": theme_id, "images": []}
    known_files = {img["file"] for img in manifest["images"]}

    cache = {}
    if os.path.isfile(cache_path):
        with open(cache_path, encoding="utf-8") as fh:
            cache = json.load(fh)

    added = 0
    photos = sorted(f for f in os.listdir(pack_dir) if f.endswith(".jpg"))
    for fname in photos:
        if fname in known_files:
            continue
        cands = parse_handpicked_candidates(fname)
        if not cands:
            print(f"  WARN {fname}: not an Unsplash download filename, skipping")
            continue

        meta = None
        pid = None
        for cand in cands:
            entry = cache.get(cand)
            if entry is None:
                try:
                    entry = api_get(PHOTO_PAGE + cand, key)
                except urllib.error.HTTPError as exc:
                    if exc.code == 404:  # wrong split; try the longer candidate
                        cache[cand] = {"__404__": True}
                        save_json(cache_path, cache)
                        continue
                    print(f"  ERR {fname}: {cand}: {exc}")
                    break  # rate limit or server error: resumable, try again later
                cache[cand] = entry
                save_json(cache_path, cache)  # checkpoint after every fetch
            if entry.get("__404__"):
                continue
            meta, pid = entry, cand
            break
        if meta is None:
            continue  # warned above, or all candidates missed

        user = meta.get("user") or {}
        dest = os.path.join(pack_dir, fname)
        if dry_run:
            print(f"  would ingest {pid} by {user.get('name') or user.get('username')}")
            added += 1
            continue
        tmp = dest + ".tmp"
        if not recompress(dest, tmp, max_edge=HANDPICKED_LONG_EDGE, quality=HANDPICKED_JPEG_QUALITY):
            print(f"  ERR {fname}: re-encode failed")
            continue
        os.replace(tmp, dest)
        w, h = longest_edge(dest)
        file_hash = hashlib.sha256(open(dest, "rb").read()).hexdigest()
        manifest["images"].append({
            "unsplashId": pid,
            "file": fname,
            "photographer": user.get("name") or user.get("username"),
            "photographerUrl": f"https://unsplash.com/@{user.get('username', '')}",
            "license": "Unsplash License",
            "licenseUrl": "https://unsplash.com/license",
            "sourceUrl": f"https://unsplash.com/photos/{pid}",
            "width": w,
            "height": h,
            "sha256": file_hash,
        })
        ledger[pid] = theme_id
        known_files.add(fname)
        added += 1
        print(f"  ingested {pid} ({w}x{h}) by {user.get('name') or user.get('username')}")

    if not dry_run and added:
        manifest["images"].sort(key=lambda im: im["unsplashId"])
        with open(manifest_path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(manifest, fh, indent=2)
            fh.write("\n")
            save_ledger(ledger)
        save_json(cache_path, cache)
    print(f"[{theme_id}] done: {added} added, pack now {len(manifest['images'])} images")
    return added


def fetch_pack(theme_id, keywords_with_quotas, key, ledger, dry_run=False):
    """Fetch images with per-sub-theme quotas. keywords_with_quotas: list of (term, quota) tuples."""
    pack_dir = os.path.join(ASSETS_DIR, slugify_theme(theme_id))
    os.makedirs(pack_dir, exist_ok=True)
    manifest_path = os.path.join(pack_dir, "manifest.json")

    # Idempotency: existing manifest entries are kept; only missing slots filled.
    manifest = {"packId": theme_id, "images": []}
    if os.path.exists(manifest_path):
        with open(manifest_path, encoding="utf-8") as fh:
            manifest = json.load(fh)
    have = {img["unsplashId"] for img in manifest["images"]}
    shipped = set(ledger.keys())
    target = sum(q for _, q in keywords_with_quotas)
    print(f"[{theme_id}] have {len(have)}, target {target}, shipped-ever {len(shipped)}")

    # Sub-theme quotas drive diversity; each (term, quota) fills its own slot.
    # Quotas count TOTAL pack composition: existing manifest entries already
    # tagged with a sub-theme are credited against that term's quota, so only
    # the deficit is fetched. Entries without a sub-theme (handpicked) don't
    # count toward any term and never block fetching.
    added = 0
    for query, quota in keywords_with_quotas:
        sub_added = sum(
            1 for img in manifest["images"]
            if img.get("subTheme") == query
        )
        page = 1
        attempts = 0
        while sub_added < quota and attempts < 12:
            attempts += 1
            results = search_photos(key, query, page)
            if not results:
                break
            page += 1
            for photo in results:
                if sub_added >= quota:
                    break
                pid = photo["id"]
                if pid in have or pid in shipped:
                    continue
                user = photo.get("user") or {}
                username = user.get("name") or user.get("username")
                if not username:
                    print(f"  skip {pid}: no photographer attribution (pack integrity rule)")
                    continue
                urls = photo.get("urls") or {}
                raw_url = urls.get("raw")
                if not raw_url:
                    continue
                w, h = photo.get("width", 0), photo.get("height", 0)
                if max(w, h) < MIN_SOURCE_LONG_EDGE:
                    continue
                tmp_src = os.path.join(pack_dir, f".tmp_{pid}_src.jpg")
                dest = os.path.join(pack_dir, f"{pid}.jpg")
                if dry_run:
                    print(f"  would fetch {pid} by {username}")
                    sub_added += 1
                    continue
                # Request the pre-sized variant: imgix params on the raw URL.
                sized = f"{raw_url}&w={TARGET_LONG_EDGE}&q={JPEG_QUALITY}&fm=jpg&fit=max"
                try:
                    download_file(sized, dest)
                except Exception as exc:  # noqa: BLE001
                    print(f"  skip {pid}: download failed ({exc})")
                    continue
                # Verify dimensions from actual bytes; trust nothing from the API.
                try:
                    rw, rh = longest_edge(dest)
                except Exception:
                    os.remove(dest)
                    continue
                if max(rw, rh) < TARGET_LONG_EDGE * 0.9:
                    os.remove(dest)
                    continue
                file_hash = hashlib.sha256(open(dest, "rb").read()).hexdigest()
                manifest["images"].append({
                    "unsplashId": pid,
                    "file": f"{pid}.jpg",
                    "subTheme": query,
                    "photographer": username,
                    "photographerUrl": f"https://unsplash.com/@{user.get('username', '')}",
                    "license": photo.get("links", {}).get("license", "Unsplash License"),
                    "licenseUrl": "https://unsplash.com/license",
                    "sourceUrl": urls.get("html", f"https://unsplash.com/photos/{pid}"),
                    "width": rw,
                    "height": rh,
                    "sha256": file_hash,
                })
                ledger[pid] = theme_id
                have.add(pid)  # guard against the same photo surfacing under two search terms
                sub_added += 1
                added += 1
                print(f"  fetched {pid} [{query}] ({rw}x{rh}) by {username}")

    if not dry_run and added:
        manifest["images"].sort(key=lambda im: im["unsplashId"])
        with open(manifest_path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(manifest, fh, indent=2)
            fh.write("\n")  # trailing newline for POSIX-friendly diffs
        save_ledger(ledger)
    print(f"[{theme_id}] done: {added} added, pack now {len(manifest['images'])}")
    return added


def validate_packs(expected_counts=None):
    """Standalone validation, mirroring checkCreativeLicenses in CI."""
    errors = []
    if not os.path.isdir(ASSETS_DIR):
        sys.exit("No creative-packs directory; nothing to validate.")
    seen_ids = set()
    for theme in sorted(os.listdir(ASSETS_DIR)):
        pack_dir = os.path.join(ASSETS_DIR, theme)
        if not os.path.isdir(pack_dir):
            continue
        manifest_path = os.path.join(pack_dir, "manifest.json")
        if not os.path.exists(manifest_path):
            errors.append(f"{theme}: manifest.json missing")
            continue
        with open(manifest_path, encoding="utf-8") as fh:
            manifest = json.load(fh)
        for img in manifest.get("images", []):
            fid = img.get("unsplashId")
            for field in ("unsplashId", "file", "photographer", "license", "licenseUrl", "sha256"):
                if not img.get(field):
                    errors.append(f"{theme}/{fid}: missing required field '{field}'")
            if fid in seen_ids:
                errors.append(f"{theme}/{fid}: duplicate Unsplash ID across packs")
            seen_ids.add(fid)
            file_path = os.path.join(pack_dir, img.get("file", ""))
            if not os.path.isfile(file_path):
                errors.append(f"{theme}/{fid}: referenced file missing on disk")
        if expected_counts and theme in expected_counts:
            n = len(manifest.get("images", []))
            if n < expected_counts[theme]:
                errors.append(f"{theme}: {n} images, expected >= {expected_counts[theme]}")
    # Cross-check the ledger: nothing in packs may be missing from it.
    if os.path.exists(LEDGER_PATH):
        ledger = load_ledger()
        for fid in seen_ids:
            if fid not in ledger:
                errors.append(f"ledger missing shipped image {fid} (never-ship-twice rule)")
    return errors


def save_json(path, data):
    """Atomic-ish JSON dump used for resumable sidecar caches."""
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(data, fh, indent=2, sort_keys=True)
        fh.write("\n")


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--theme", help="catalog preset id, e.g. fresh-air")
    g.add_argument("--all", action="store_true", help="fetch for all catalog presets")
    g.add_argument("--handpicked", metavar="THEME", help="ingest hand-picked Unsplash downloads already in the pack dir")
    g.add_argument("--validate", action="store_true", help="validate packs, no network")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    if args.validate:
        errs = validate_packs()
        if errs:
            print("VALIDATION FAILED:")
            for e in errs:
                print(f"  - {e}")
            sys.exit(1)
        print("Validation passed.")
        return

    # Per-sub-theme quotas replace flat keyword lists: each (search term, quota)
    # pair guarantees its slice of the pack. Terms target high-potency imagery
    # archetypes per docs/research/imagery-domains.md (awe + soft fascination,
    # down-regulation rather than adrenaline). Target 60+/pack: quotas sum
    # slightly above 60 for fresh-air because sub-theme diversity beats a
    # rigid count; undershoots on availability are acceptable.
    # (forgetting-curve rationale in docs/research/imagery-domains.md).
    THEME_QUOTAS = {
        "hydration": [
            # Water drinks only (user decision, Oct 2026): the pack must show
            # appetizing, drinkable water — glasses, bottles, pitchers, ideally
            # garnished. Scenery (streams, waves, dew, ripples) and bare fruit
            # are out; they read as abstract nature, not as a hydration cue.
            ("glass of water", 12),
            ("sparkling water pour glass", 12),
            ("lemon cucumber infused water pitcher", 12),
            ("mint berry infused water bottle", 12),
            ("water bottle condensation", 12),
        ],
        "fresh-air": [
            ("misty mountain peaks layers", 7),
            ("golden hour meadow grass backlight", 6),
            ("calm lake reflection dawn", 6),
            ("wispy clouds open sky", 5),
            ("coastal cliff ocean breeze", 6),
            ("forest path dappled light", 6),
            ("macro dew leaf morning", 5),
            ("desert dune minimal", 5),
            ("starry night sky stars", 5),
            ("snowy summit ridge", 5),
            ("waterfall moss grotto", 5),
            ("rolling hills pasture fog", 5),
        ],
        "fruit": [
            ("ripe peaches close-up", 10),
            ("berry macro water droplets", 10),
            ("citrus slices vibrant", 10),
            ("watermelon splash summer", 8),
            ("orchard harvest sunlight", 10),
            ("dragon fruit exotic macro", 12),
        ],
        "vegetables": [
            # Produce-explicit search terms only (user decision, Oct 2026):
            # vague terms like "garden harvest basket" and "leafy greens macro"
            # pulled in leaves, flowers, fruit baskets, and scenery.
            ("farmers market vegetable stall", 10),
            ("heirloom tomato close-up", 10),
            ("basket of fresh vegetables", 10),
            ("colorful bell peppers", 10),
            ("fresh salad bowl vegetables", 12),
            ("carrots and broccoli fresh", 6),
        ],
    }

    themes = list(THEME_QUOTAS.keys()) if args.all else [args.theme]
    if args.theme and args.theme not in THEME_QUOTAS:
        sys.exit(f"Unknown theme '{args.theme}'. Known: {', '.join(THEME_QUOTAS)}")

    key = load_secrets()
    ledger = load_ledger()
    if args.handpicked:
        handpicked_pack(args.handpicked, key, ledger, args.dry_run)
    else:
        for theme in themes:
            fetch_pack(theme, THEME_QUOTAS[theme], key, ledger, args.dry_run)
    errs = validate_packs()
    if errs:
        print("POST-FETCH VALIDATION FAILED:")
        for e in errs:
            print(f"  - {e}")
        sys.exit(1)
    print("All packs valid.")


if __name__ == "__main__":
    main()
