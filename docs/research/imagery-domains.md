# Imagery Domains — Evidence for Specific Lifestyle Targets

Per-domain guidance for curating image packs, with sourcing rules and copy themes.

## Universal curation rules

1. **Positive valence, high aesthetic quality.** Evaluative conditioning transfers
   affect from the image to the paired behavior — every image must genuinely look
   *appealing* (well-lit, appetizing/aspirational, professional quality), never
   clinical or guilt-inducing (no sad wilted lettuce, no dirty dishes).
2. **Concrete and sensory.** Show the *experience* (condensation on a glass, sun
   through leaves, steam over a colorful bowl), not abstractions or text-heavy
   posters. Per picture superiority effect + dual coding.
3. **Variety within theme.** Minimum viable pack: 8–16 images per goal theme so
   fatigue-aware rotation has room to work. Curate broadly across sub-themes (below).
4. **License compliance.** Only properly licensed imagery ships with the app
   (CC0/public domain, or self-commissioned). Attribution required where applicable.
   Curated *remote* packs may fetch from open APIs (e.g., Openverse) but must show
   license info. See docs/legal-and-licensing notes in CONTRIBUTING.
5. **No people in negative states.** No sick people, shaming imagery, fear appeals
   for wellness goals (they backfire: defensive avoidance per fear-appeal research).
6. **Diversity of settings/people.** Reflect a broad range of bodies, ages, settings
   so aspirational identification works for many users (self-congruence for everyone).

## Hydration

**Evidence base:** Field studies of hydration reminders show prompted drinking works
best when tied to routine anchors (meals, waking) and sensor/intervention loops
(Hydroprompt; water-bottle ambient feedback studies). Glass shape/appearance affects
consumption (straight vs. sloped glassware research), suggesting visual presentation
matters even for water. General appetite research: visual food cues trigger
anticipatory responses — analogously, appealing water imagery primes the behavior.

**Sub-themes for image packs:** crystal-clear glass with condensation; water poured
into glass (motion frozen); icy water in sunlight; fruit-infused water color; blue/
teal/white palette; mountain streams and springs; morning bedside glass.

**Copy pools:** refresh, clarity, energy, skin glow, steady hands, "your body is
X% water" trivia (light), ocean/rain soundscape references.

## Plant-based whole foods

**Evidence base:** Visual appeal is one of the strongest predictors of vegetable
intake ("foodies" eat more veg); "food porn" style attractive plating increases
willingness to try (beautiful food presentation studies). Variety perception
increases intake (buffet/variety research). Social norms imagery (people enjoying
vegetables) leverages descriptive norms.

**Sub-themes:** rainbow platters; vibrant macro textures (pomegranate seeds, citrus
cross-sections, berries); steam rising from hearty grain bowls; sizzling tofu/
tempeh dishes styled beautifully; farmers-market scenes; growing/soil-to-table
narratives; spice markets and color.

**Copy pools:** vibrancy, nourishment, color, "eat the rainbow", farmer's pride,
flavor-forward language (herbs, char, sweetness) — emphasize *deliciousness*,
not moralized health ("should"). Delectable > dutiful.

## Time in nature / green time

**Evidence base:** Attention Restoration Theory (Kaplan & Kaplan) and Stress
Reduction Theory (Ulrich): even brief *viewing* of nature imagery measurably reduces
stress markers and restores directed attention (systematic reviews of indoor
viewing experiments). Berman et al. 2008 confirmed cognitive benefits of interacting
with nature. Key characteristics: water features, canopy/savanna-like vistas,
depth cues, gentle fascination stimuli ("soft fascination").

**Sub-themes:** forest paths and dappled light; lakes/rivers/seascapes at golden
hour; mountains with layers; slow clouds; moss, fern, dew macros; starry nights;
window views of gardens; biophilic interiors with plants.

**Copy pools:** exhale, unplug, wonder, spaciousness, "soft fascination", awe.
Use sparingly and calmly — this domain is about *down-regulation*, so imagery
should be restful, not adrenaline-coded.

**Special power:** nature imagery pulls double duty — it primes the goal AND
delivers a micro-dose of the goal's benefit (restoration) at glance time. Prioritize
quality here; it's the strongest evidence-backed image category for wallpaper use.

## Breathing exercises / mindfulness

**Evidence base:** Slow-breathing interventions reliably reduce acute stress
markers; visual pacing cues (expanding shapes, waves) support breath alignment
(breathing-app research). Consistent daily practice beats long-but-rare sessions.

**Sub-themes:** calm gradients (dusk sky, deep water); expanding ripple/circle
motifs; smoke/vapor curls; dandelion seeds; minimalist line art of breath rhythm;
soft-focus horizons.

**Copy pools:** "breathe", slow down, box breathing, here and now, 4-7-8 references,
grounding through senses.

**Interactive option (future):** lock-screen breathing overlay widget — a genuinely
useful micro-intervention rather than pure promotion.

## General wellness / movement / sleep

**Sub-themes:** morning light stretching, cozy evening wind-down scenes, warm
palettes, tidy serene spaces, hands around warm mugs, sunset walks.

**Copy pools:** recharge, restore, ritual, gentle strength, tide-like rhythms.

## Anti-patterns (all domains)

- Fear, disgust, shame (fear appeals backfire without high efficacy messaging)
- Perfectionist/unattainable ideals that create distance vs. self ("not for me")
- Overly literal stock-photo vibes (undermines authenticity; amateurish curation
  undermines the conditioning transfer)
- Text-in-image (scales poorly across languages and wallpaper crops; keep copy in
  notification body instead)

## Sourcing pipeline (shipped approach — dev-side curation, offline app)

- The app is 100% offline: creatives are fetched **at development time** by
  `scripts/fetch_creatives.py`, committed to the repo, and bundled into the APK.
  No network permission exists; no API key ever ships.
- Unsplash is the primary source. Two ingest modes:
  - **Hand-picked (preferred):** a maintainer browses unsplash.com, downloads
    selections as `<slug>-<id>-unsplash.jpg` into the pack dir, and runs
    `python scripts/fetch_creatives.py --handpicked <theme>`. The script parses the
    photo ID from the filename, fetches attribution via the API (resumable
    sidecar cache — the demo key is capped at 50 req/hr), re-encodes to 2160 px
    long-edge JPEG q85 (visually indistinguishable from the original on a modern
    high-res display), and writes the manifest + ledger. Manual curation exists
    because search-term relevance proved unreliable for subject correctness and
    vibrancy (auto-fetched fruit contained pines and flowers).
  - **Auto-fetch (legacy, quotas):** search-ranked, portrait orientation,
    ≥1600 px source, re-encoded to 1440 px q78. Retained for bulk drafts only;
    packs it produced have historically required manual weeding afterward.
- Verify license per image at ingest; store attribution + license URL in the pack's
  manifest (JSON). Manifests live in-repo; `./gradlew :app:checkCreativeLicenses`
  fails the build on any missing field, duplicate Unsplash ID, or missing file.
- **Never-ship-twice rule:** `.creative-ledger.json` records every shipped photo ID
  forever. Quarterly refreshes draw only from never-before-shipped photos.

### Pack size derivation (forgetting-curve budget)

First-release target: **60 images per category**. Wallpaper exposure decays
approximately exponentially (Ebbinghaus form `R = e^(-t/S)`); a repeat feels fresh
when retention drops below conscious noticing (~R < 0.5, i.e. t > 0.69·S).

| Image salience | S (days) |
|---|---|
| Low-salience glance | ~7 |
| Distinctive photo (golden hour, vivid) | ~14 |
| Highly memorable "wow" shot | ~30 |

Worst case is the hydration preset at 4 wallpapers/day: N images give cycle time
N/4 days. At N=60, cycle = 15 days → R = e^(-15/14) ≈ 34% for distinctive shots
(fresh), and the rotator's fatigue-aware selection plus deck-reshuffle guard
(no repeat within a shuffled deck) extends effective gaps further. Below ~40,
mid-salience images repeat while still consciously recognized; 60 is the knee of
the curve. Quarterly expansions compound the effect over the app's lifetime.
APK cost: ~60 images ≈ 15–20 MB per category (1440px JPEGs), well inside the
Play AAB base limit.
