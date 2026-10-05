# Retarget

**Turn advertising's own toolbox toward your goals.**

You know how ads follow you around the internet — promoting someone else's goals?
Retarget flips that: you're the advertiser now, and the product is the life you
actually want. Your phone's wallpaper becomes a billboard *for your goals*.

[![Phase: 2 — The Campaign](https://img.shields.io/badge/phase-2%20The%20Campaign-brightgreen)]()
[![License: AGPL-3.0](https://img.shields.io/badge/license-AGPL--3.0-green)](LICENSE)

## What it does

Instead of renting your attention to ad networks, Retarget runs a **personal
advertising campaign for your own goals**:

- 🖼️ **Wallpaper as billboard.** Your wallpaper rotates through curated, positively
  valenced imagery of your target lifestyle — clear water glasses, vibrant produce,
  forest trails — on a research-informed schedule (mere exposure effect).
- 🔔 **Ad-style notifications.** Beautiful, image-led nudges for habits you chose,
  delivered within strict frequency budgets and quiet hours.
- 🧠 **Fatigue-aware rotation.** Every image and message is rotated before it wears
  out, exactly like an ad campaign rotates creative to fight ad fatigue.
- 📊 **Your campaign at a glance.** See exposures, check-ins, and pacing — all data
  stays on your device, exportable, deletable in one tap.

Every nudge is user-initiated, transparent ("why am I seeing this?"), reversible, and
paced to stay pleasant — never nagging. See
[digital-wellbeing research](docs/research/digital-wellbeing.md) for the guardrails.

## Status

**Phase 2 complete — “The Campaign.”** Multi-channel nudge delivery (wallpaper +
image-led notifications with check-ins and transparency controls) is merged and
tested. Next up: Phase 3 — “The Agency” (widgets, adaptivity, data export). See
[DEVELOPMENT.md](DEVELOPMENT.md#feature-phases) for the roadmap.

| Phase | Theme | Status |
|---|---|---|
| 0 | Foundation (repo, CI, docs) | ✅ Complete — v0.1.0 released |
| 1 | MVP "The Billboard" (wallpaper engine) | ✅ Complete — Goal wizard + wallpaper engine shipped |
| 2 | "The Campaign" (notifications + scheduler) | ✅ Complete — v0.3.0 released |
| 3 | "The Agency" (adaptivity, widgets, bundles) | 🚧 Next — Planning phase |
| 4 | Polish & community | Planned |

## Getting involved

- **Contributing:** read [AGENTS.md](AGENTS.md) (rules for humans and AI
  contributors alike) and [DEVELOPMENT.md](DEVELOPMENT.md).
- **Research library:** [docs/research/](docs/research/README.md) — the evidence
  behind every design decision, with citations.
- **License:** [LICENSE](LICENSE)

## Development notes

- **Creative fetching (issue #4):** `scripts/fetch_creatives.py` downloads Unsplash images per preset theme, writes per-pack manifests with mandatory license/photographer metadata, and records shipped IDs in `.creative-ledger.json` to ensure nothing ships twice. Requires Python 3.10+, optionally Pillow for recompression (`pip install Pillow`). API key in `secrets.properties` (copy from `secrets.properties.template`). Run `./gradlew :app:checkCreativeLicenses` to validate packs before committing.

- **Quarterly refresh cadence:** Run the fetch script every quarter to expand the image library; it only pulls new IDs (never-duplicates), so each release adds fresh content.
