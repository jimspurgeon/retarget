# Transparency Screen Documentation

Plain-language explanations of every nudge channel, matching what the in-app
"Why am I seeing this?" screen tells the user. Each channel entry states what
it does, when it acts, and how to turn it off — no jargon, no euphemisms
(AGENTS.md §2: transparency is a core product feature, not an afterthought).

---

## Lock-screen ticker (M3.3)

**What it is:** a silent, low-priority notification that shows a short goal
ticker on your lock screen — for example, "2 nudges today for Hydration".
It never buzzes, never makes a sound, and never pops over what you're doing;
it's just there when you glance at your phone.

**How it behaves:**

- **Opt-in, off by default.** The ticker only runs if you turn it on in
  Settings (or during onboarding). Lock-screen presence is the most intrusive
  surface the app offers, so nothing appears there without your explicit say-so.
- **Hard cap: 2 per day.** The ticker can never show more than twice a day,
  regardless of settings.
- **Quiet hours respected.** New tickers are not delivered during quiet hours
  (22:00–07:00 by default), and a ticker already showing is removed shortly
  after quiet hours begin — typically within a couple of hours, since the
  removal check runs on the app's periodic schedule.
- **Crowding-coordinated.** The scheduler counts ticker slots against the same
  crowding backoff as wallpaper and notification nudges, so turning the ticker
  on can't double your total interruption.

**How to turn it off:** Settings → "Lock-screen ticker" toggle. Turning it off
takes effect immediately — no residual notifications, nothing to clean up.

**Why this design:** the lock screen is treated as an ambient glance surface
(a persistent, non-alerting "goal ticker"), not an alert. Research basis:
[docs/research/android-platform.md](research/android-platform.md) §6
(lock-screen & always-on ambient surfaces). Ethical guardrails per
[AGENTS.md](../AGENTS.md) §2: user-initiated, reversible, off by default, and
explainable in plain language.

---

## Adaptive scheduling and creative mix (M3.4)

**What it is:** the app learns from your responses which times of day and
which creative themes work better *for you*, and gently shifts future
nudges toward what works. This is the same kind of optimization advertisers
use — pointed at your own goal, on your device only.

**What adapts:**

- **Timing.** Times of day where you check in more get slightly higher
  scheduling priority (within a fixed, gentle range: at most ±50%).
- **Creative mix.** Themes you respond well to appear a bit more often;
  themes you tap "Fewer like this" on appear less often.

**What never adapts:**

- **Daily caps.** Hard limits per channel (e.g., ticker: 2/day) are
  immutable — learning reorders nudges, never adds more.
- **Quiet hours.** 22:00–07:00 by default; no learning can deliver there.
- **Your goal data.** All learning stays on this device, is included in
  "Export my data", and can be wiped with "Reset learning" in Settings.

**How it learns:** only from your own actions — check-ins (strong
positive), "Fewer like this" taps (strong negative), and snoozes (mild
negative). Nothing else is measured; nothing leaves the device. Selection
among near-tied options keeps some variety (the rotator's randomization),
but learning itself is deterministic — no random exploration is applied.

**How to turn it off:** Settings → "Reset learning" clears everything
learned so far. Learning is conservative by design: it only kicks in after
roughly 10 observations for a given time-of-day slot, and starts from a
neutral prior, so early noise can't send it off the rails.
