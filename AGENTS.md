# AGENTS.md

Guidance for AI agents and human contributors working on **retarget**, an open source
Android application that lets users apply advertiser-style nudging techniques to their
own goals and habits. Everything in this file applies to every contribution: code, docs,
issues, commit messages, and pull request descriptions.

## Project Overview

- **What it is:** An Android app (Kotlin, Gradle) that borrows engagement and nudging
  techniques from the advertising industry and turns them toward user-chosen goals.
- **License:** See [LICENSE](LICENSE). All contributions are made under that license.
- **Repo:** https://github.com/jimspurgeon/retarget
- **Plan & workflow:** [DEVELOPMENT.md](DEVELOPMENT.md) is the canonical product plan,
  architecture, and branching strategy. **Read it before working on anything.**
- **Evidence:** [docs/research/](docs/research/README.md) is the research library that
  justifies design decisions. Features cite it; check it before proposing
  behavior-affecting changes.

---

## Core Rules (the one-page version)

Everything below is expanded in §1 through §7. If context is tight, follow this page and
link the specific section before doing anything it governs.

1. **Nothing secret, personal, or proprietary ever enters the repo.** Not in files,
   not in commits, not in history. If you find any, stop, do not push, report it. (§1)
2. **Local-first, no tracking.** Behavioral data stays on-device. No analytics, ad SDKs,
   or telemetry. New permissions need documented justification. (§1, §2)
3. **Nudges serve the user, only.** User-initiated, reversible, explained in plain
   language. No dark patterns, no influencing third parties. (§2)
4. **The human is the reviewer of record.** Your conclusions are proposals, not facts.
   Your job is to make their review cheap and verify claims with evidence they can
   check. (§5)
5. **Four hard gates stop all work until approved:** design (nontrivial code), edge-case
   (ambiguous requirements, user-visible choices), pre-commit, pre-push. Silence is
   not approval; approvals are scoped to the exact commits they were given. (§5.1)
6. **Escalate only what is decision-shaped.** A question gates work only when resolving
   it could plausibly change what the human would want. Everything else is decided
   provisionally and logged for review. Inflationary questioning is a failure mode,
   not diligence. (§5.1.1)
7. **Verification claims come from CI, not from memory.** Local run transcripts are
   provisional until CI corroborates them. Quoted code will be spot-checked; a
   mismatch voids the report. (§5.4)
8. **Classify before reporting: Minor, Standard, or Major.** Review depth scales with
   blast radius, in both directions. Tier inflation and tier deflation are both
   rejections. (§5.2)
9. **One logical change per commit.** Feature branches only, never commit to `main`,
   never rewrite published history. Pushing follows the gate in §5.1 or a standing
   approval under §7.3. Conventional commit subjects on every commit, including
   merges made during integration (`feat: merge <branch> — <summary>`, never bare
   "Merge <branch>: ..."). (§3)
10. **Builds, tests, and honest reporting.** Verify what you can, state plainly what
    you could not run, and never claim success without a real command and its output. (§4, §5.4)
11. **Multi-agent work follows the orchestration protocol.** Parallel workers get
    self-contained specs with anti-idle instructions; every integration is followed
    by a full build+test and a gatekeeper review before the human sees the PR. (§7)

---

## 1. Security & Confidentiality — Non-Negotiable Rules

This is a **public repository**. Assume everything committed is visible to the world,
forever (including in git history). Before committing, pushing, or pasting anything:

### Never commit or disclose
- **Secrets of any kind**: API keys, signing keys, keystores (`.jks`/`.keystore` are
  gitignored, never work around that), tokens, passwords, OAuth client secrets,
  `google-services.json`, `local.properties`, or debug/base64-encoded variants of these.
- **Personal or user data**: real names, emails, phone numbers, device identifiers,
  location traces, or analytics dumps. Use synthetic/fake data in tests, fixtures, and
  docs. This includes data belonging to the maintainer; never paste real exports from
  a personal device, calendar, or usage log.
- **Private infrastructure**: internal hostnames, IPs, SSH configs, CI credentials,
  or anything from the maintainer's local environment.
- **Proprietary material**: no code, assets, or documentation copied from a current or
  former employer or any closed-source project. Advertising techniques used as
  *inspiration* should be described generically or sourced from public material.

### Operational rules
- If a secret is discovered in the repo or in a working file, **stop, do not push**,
  report it, and let the maintainer rotate the credential and handle history rewriting
  (e.g., with `git filter-repo`). Do not attempt to fix history silently.
- Never disable or weaken `.gitignore` entries for secret-like files. Extend it when new
  config types appear.
- New dependencies must be declared in version catalogs / Gradle files only, never
  vendored as compiled binaries or tarballs of unknown provenance.
- Keys needed for local development go in untracked files (e.g., a template checked in
  as `secrets.example.properties`, with the real file gitignored).

### App-level privacy (this category of work matters extra)
This app is *about* behavioral influence. That makes trust and privacy core product
features, not afterthoughts:

- The app must be **local-first**: user goal data stays on-device by default. Adding
  network transmission of behavioral data requires explicit user consent UI, a written
  rationale in the PR, and maintainer approval.
- Do not add analytics, ads SDKs, trackers, or third-party telemetry. This project's
  entire premise is giving users advertiser techniques *for themselves*; embedding an
  actual ad network would be both hypocritical and a hard rejection.
- Any permission requested (notifications, exact alarms, accessibility, etc.) needs a
  documented, minimal justification. Avoid requesting permissions "for later."

---

## 2. Ethical Guardrails for Nudge Features

Features implement persuasion techniques intentionally. Keep them on the right side of
the line:

- **User-initiated only.** Every nudge targets a goal the user explicitly created.
  Never add nudges aimed at app retention for its own sake (no "come back" spam, no
  streak guilt-tripping unless the user enabled streaks).
- **No dark patterns.** No manipulative UI toward the user: fake urgency, hidden
  opt-outs, confirm-shaming, obstructed cancellation or deletion, or pre-checked
  consent boxes.
- **Reversible and controllable.** Every nudge feature needs a discoverable off switch
  at least as easy to find as the feature itself. Users must be able to inspect, edit,
  export, and fully delete their data and goals.
- **Transparency.** Techniques borrowed from advertising should be explainable in plain
  language in-app (e.g., "this reminder is scheduled at your peak responsive time").
- **Framing.** Language in code, docs, and UI should reflect *self-directed* behavior
  change ("nudge," "prompt," "cue"), not covert manipulation of others. This app nudges
  **oneself**; features designed to influence other people without their knowledge are
  out of scope and must be flagged.

If a feature request seems to cross into manipulating third parties or overriding user
intent, raise it in an issue before implementing.

---

## 3. Repository Workflow (applies to all contributors, human and agent)

### Git hygiene
- **Commit messages:** imperative mood, present tense ("Add streak scheduler", not
  "Added..."). Reference issues where applicable ("Closes #12").
- **Never rewrite published history** (`push --force`, rebase onto main) without
  maintainer instruction. Never commit directly to `main` — work in feature branches
  named `feat/...`, `fix/...`, `chore/...`, `docs/...`.
- **Atomic PRs:** one logical change per PR. Keep them reviewable (< ~400 lines of diff
  where practical). Include *what* and *why* in the description, not just screenshots.
- **Don't push** unless asked, and when asked, only after the pre-push gate in §5.1 is
  satisfied (or a standing approval under §7.3 is in force). Leave commits local or in
  a branch for review. Merging into `main` always requires the human's explicit
  action or instruction — no standing approval covers it.

### Community conduct
- Be respectful and assume good faith in issues and reviews.
- Discuss significant design decisions (architecture, new dependencies, permission
  changes) in an issue or PR **before** writing lots of code.
- Generated content (AI-assisted or otherwise) is welcome, but the submitter is
  responsible for every line in the PR: verify it compiles, passes tests, and follows
  these rules.
- Respect the license: no GPL/aggressive-copyleft code into a repo licensed otherwise
  without maintainer sign-off; keep third-party snippets attributed.

### Documentation
- Update `README.md` when adding setup steps, features, or changing build requirements.
- New user-facing features need a brief note in docs or the changelog in the same PR.
- Keep comments explaining *intent*; the code shows the *how*.

---

## 4. Android Engineering Standards

- **Language/build:** Kotlin first. New modules use Gradle Kotlin DSL. Don't introduce
  Groovy DSL, XML-heavy patterns, or Java without reason.
- **Runs locally:** before marking work done, the project must assemble
  (`./gradlew assembleDebug`) and pass whatever test suite exists (`./gradlew test`).
  Report honestly if you couldn't run them (e.g., no SDK/AVD available) rather than
  claiming success.
- **Compatibility:** respect the declared `minSdk`; avoid gated APIs without version
  checks. Target the latest stable SDK unless the repo says otherwise.
- **Testing:** new logic (schedulers, nudge engines, scoring/ranking algorithms) needs
  unit tests. UI changes should be verifiable via screenshot or simple instrumentation
  test where feasible.
- **Architecture:** follow existing structure (view models, repositories, DI setup).
  Don't invent a parallel pattern mid-project. If no structure is established yet,
  propose one in an issue first.
- **Dependencies:** prefer well-known, actively maintained libraries; pin versions;
  check licenses for compatibility.
- **Resources:** no hardcoded user-visible strings. Use `strings.xml` and keep future
  localization possible.

---

## 5. Agent Review Protocol

These rules govern how any AI agent working in this repository reports its changes and
seeks approval. The objective: the human operator can review, verify, and veto every
change with minimal manual effort, without having to trust the agent's self-assessment.
The human is the reviewer of record. The agent proposes; the human disposes. Scrutiny is
proportional: small changes move through lightweight reports, while large ones earn
deep, evidence-dense review before they go anywhere (§5.2).

**Prime directive.** An agent's job is to reduce the human's review workload by
collecting and presenting evidence, not to substitute its judgment for the human's.
Anything the agent concludes is a *proposal*, not a fact. Every claim about the code,
the build, or the tests must be backed by evidence the human can check: a
copy-pasteable command, a `file:line` reference with a verbatim quote, or a CI run.
If the human would have to re-derive a fact to trust it, the agent hasn't finished
the job.

**Human judgment is supreme.** The human operator is the ultimate authority on every
question of intent, priority, risk tolerance, and product direction, and the best judge
of what to do whenever anything is uncertain. The agent's analysis exists to *inform*
that judgment, never to replace or preempt it. Corollaries:

- **Approval cannot be manufactured.** Framing, ordering, or repeated asking may not be
  used to obtain consent. If the human hesitates or asks a question back, that is
  engagement to be answered, not resistance to be worn down.
- **The human defines the work.** Scope, priorities, and what "done" means come from the
  human. An agent may surface consequences, costs, and trade-offs, including open
  disagreement, but never reorders goals, expands scope, or narrows requirements on its
  own authority.
- **Delegation is scoped and revocable.** The human may defer a call back to the agent
  ("use your judgment here"). That delegation covers exactly the matter at hand, is
  revocable at any time, and never transfers to future similar decisions.

### 5.1 Mandatory gates — when the agent MUST stop and ask

Do not proceed past any of these points without explicit human approval. Silence,
absence of objection, or a stalled conversation is **not** approval. Approvals are
scoped to the exact commits they were given, never to "future similar changes," and
re-classification of a change's tier is the human's prerogative alone.

1. **Design gate** — before writing nontrivial code (roughly >50 lines, or anything
   touching architecture, permissions, dependencies, data persistence, or nudge
   behavior). Present the problem, the options considered, and a recommendation, per
   §5.5. Iterate here; code is cheaper to rewrite before it exists.
2. **Edge-case gate** — the moment the agent notices an ambiguous requirement or an
   unhandled edge case *that is decision-shaped*: resolving it could plausibly change
   what the human would want, or the choice is user-visible. Present per §5.5.
3. **Pre-commit gate** — before running `git commit`. Present the proposed commit
   message and the diff, organized per §5.3.
4. **Pre-push gate** — before `git push` (this reinforces §3's "don't push unless
   asked"). Push only after the human approves the complete change report. If anything
   material changed since the last approval, re-present. Final judgment on whether the
   change ships belongs to the human alone.

Additionally, stop **immediately**, mid-task and not at the next gate, when any of
these occur:

- A secret, personal data, or proprietary material is discovered anywhere (§1 rules
  then take over: stop, do not push, report).
- A change touches an ethical guardrail (§2): nudge behavior, permissions, data
  handling.
- Verification fails in a way the agent cannot explain or fix confidently.
- The agent realizes something it previously told the human was inaccurate. Correct
  the record explicitly; never quietly supersede an earlier claim.

#### 5.1.1 Escalation discipline

Asking is not free. A question gates work only when it is **decision-shaped**: resolving
it could plausibly change what the human would want. Concretely:

- **Gate** (stops work, §5.5 format): choices affecting behavior, permissions, data
  handling, architecture, public API, or anything user-visible.
- **Decide and log** (continues, recorded in the change report): purely internal,
  easily reversed choices (naming, small refactors, implementation order, which of two
  standard library approaches to use). State the default you applied, the alternative
  you rejected, and one line on why, so the human can veto in review.

Batching related logging-tier choices into the report is fine. Converting logging-tier
choices into gating questions pads the queue, teaches the human to skim, and is treated
as a review-process failure in its own right. The failure mode this doc guards against
is not "agent decided alone"; it is "the human stopped reading the questions."

### 5.2 Proportionate review intensity

Every gate in §5.1 is mandatory regardless of size, but review *depth* scales with the
blast radius of the change. Classify the change before reporting it, state the
classification and its justification, and let the human re-classify at will (in
particular upward):

- **Minor** — typo, comment, or doc-only fixes, formatting, resource-string additions,
  or a logic change under ~20 LoC whose blast radius is contained to itself. Expedited
  report: one compact message with the full diff, the verification command and its
  output, and a one-line annotation per relevant checklist box. Small is not exempt
  from the gates, just cheap to review.
- **Standard** — a single-component logic change with tests, no impact on
  architecture, permissions, persistence, or nudge behavior. Full change report per
  §5.3; decisions already cleared at the design gate may be summarized with pointers
  to that conversation.
- **Major** — anything that adds or changes a permission, touches persistence or
  migrations, alters nudge behavior or scheduling, changes public API or module
  boundaries, introduces a dependency, exceeds roughly 400 LoC of diff, spans multiple
  logical commits, or is destined for a PR into `main`. Full change report **plus**
  every deep-scrutiny requirement below, before the pre-push gate.

Deep-scrutiny requirements for Major changes (all required):

- **Per-commit walkthrough.** Each commit gets its own verification evidence and an
  explicit statement of what a reviewer should check in *that* commit. No "see the diff."
- **Adversarial self-review.** The agent writes, before presenting, the strongest case
  *against* its own change: inputs that would break it, interactions it might have
  missed, assumptions that could be false, and what a hostile reviewer would attack
  first. Included verbatim; the human decides how much weight it deserves.
- **Judgment-call cadence.** At least one §5.5-format decision per logical component.
  If none surfaced, say so and explain what you checked to confirm none existed, rather
  than manufacturing one.
- **Review kit.** An ordered file list for review, exact reproduction commands, and
  the two to four highest-risk spots each with pinned evidence per §5.6, so the human
  can verify the risky parts first and skim the rest.

Tier mistakes in both directions are failures: steamrolling a Major change through a
Minor-style report, and burying a typo fix in Major-level ceremony.

### 5.3 The change report (required at the pre-commit and pre-push gates)

Gates 3 and 4 use the same artifact, built incrementally so the human can review early
and often. A change report without all of these sections is incomplete:

1. **Requested vs. delivered.** Quote the task as given. List every deviation from it
   and the reason. "Did what was asked plus a drive-by refactor" must be visible, not
   buried in the diff.
2. **Commit map.** For each commit: hash, message, files changed with
   insertions/deletions, and one sentence of intent. Commits must isolate logical
   changes (see §5.6) so the human can approve or reject them independently.
3. **Design decisions.** Every point where the agent chose among alternatives during
   implementation, separated into gated decisions (cleared with the human, summarized
   with pointers) and logged decisions (per §5.1.1, with the default applied and the
   rejected alternative). For each: the options, the chosen one, and why, in the §5.5
   format.
4. **Edge cases.** A numbered list. For each: (a) the triggering condition, stated
   precisely; (b) the behavior implemented; (c) a verbatim quote of the code that
   handles it with `file:line`; (d) the test that exercises it, or an honest
   explanation of why no test exists and what risk that leaves. Explicitly list edge
   cases the agent identified but deliberately did not handle, and why.
5. **Verification.** The exact commands run, the real exit codes, and the tail of the
   actual output, verbatim. Per §5.4, local output is provisional evidence; where CI
   has run on the branch, link the run and treat it as authoritative. Include failed
   runs and flaky reruns; a red-then-green story is information the human needs. If
   verification could not be run (no SDK, etc.), say so plainly rather than implying
   success.
6. **Self-audit.** Run the §6 checklist and annotate *every* box with its evidence
   (command, output, or `file:line` quote), not just a checkmark. "I looked at the diff
   and it seems fine" is not evidence.
7. **Uncertainty ledger.** Things the agent is unsure about, ranked by severity, each
   with the cheapest command or inspection that would resolve it. Honest uncertainty
   here is a feature; its absence is a red flag. Any uncertainty that is
   decision-shaped must already have been escalated as a §5.1.1 gate, not parked here.
8. **Open questions and search report.** Genuinely open decisions, each presented per
   §5.5. If there are none, state that explicitly *and* describe what you searched for
   to confirm none existed (ambiguities you resolved from docs, edge cases you traced
   and found determinate answers for). What is being probed is the search process, not
   the question count.

### 5.4 Evidence rules and trust boundaries

- **CI is the source of truth for builds and tests.** An agent's transcript of a
  local run is provisional evidence: plausible, checkable, but produced by the same
  party making the claim. Where the repo has CI configured, verification claims are
  only conclusive when backed by a CI run on the branch. Report the local run
  (commands, exit codes, output tails) as a preview, and link the CI run for
  confirmation. Where CI is not yet configured, an independent gatekeeper re-run (§7.4)
  is the strongest available corroboration.
- **Quotes are provisional and sampled.** Every `file:line` quote must be verbatim.
  The human will spot-check a random sample of quotes against the repo. A single
  mismatch between a quoted excerpt and the actual file voids the report and resets
  trust in every other claim in it. This is not pedantry; it is the cheapest
  corruption detector in the protocol.
- **Separate observation from inference.** "CreativeRotatorTest passes (ran
  `./gradlew test --tests CreativeRotatorTest`, exit 0)" is an observation. "This
  should be thread-safe" is an inference and must be labeled as such, with the
  reasoning shown so it can be attacked.
- **Make commands reproducible.** Every verification command must be copy-pasteable
  as-is from the repo root, and deterministic wherever possible.
- **Report failures verbatim.** Include stderr tails and exit codes. Re-running a
  failing build until it passes, without reporting the failures, is grounds for
  automatic rejection of the change.
- **Anchor to commits.** When describing the state of the repo, reference commit
  hashes so statements survive subsequent changes.

### 5.5 Presenting options and reasoning

For every decision submitted to the human (design gate, edge-case gate, or open
question in the report), use this format:

- **Decision:** one sentence stating what is being chosen.
- **Context:** two to four sentences of background, with `file:line` anchors to the
  code or docs that make this decision necessary.
- **Options:** each option gets: what changes concretely; blast radius (files and
  behaviors affected); cost to implement; cost to reverse later; the specific risk
  of being wrong and how that risk would manifest; supporting evidence.
- **Recommendation:** one option, with the reasoning chain spelled out so the human
  can find the weak link. If the recommendation depends on an assumption about intent
  or requirements, name the assumption.

Ground rules:

- Never present exactly one option unless no alternative genuinely exists, and then
  say so explicitly, so the human knows it's exhaustive rather than lazy.
- No false balance. If one option is clearly correct, say so and explain why, rather
  than staging a debate. But if a rejected option is plausible, keep it in the list;
  humans are good at catching what silently disappeared.
- **Interact with challenges.** If the human disputes a recommendation, re-present the
  options with their objection incorporated as a constraint. Don't relitigate the old
  framing. Their read of the situation outranks the agent's; update the analysis,
  don't defend it.
- **Define terminology on first use** rather than avoiding it. Clarity of explanation
  is required; oversimplification is not.
- One decision per message. Bundling unrelated decisions forces all-or-nothing
  answers.
- Quantify where possible ("~40 LoC in 2 files", "adds one Gradle module", "no DB
  migration") instead of adjectives.
- **Don't dumb it down.** Use precise terminology and name the actual mechanism:
  "race between WorkManager enqueue and the BOOT_COMPLETED receiver", not "a timing
  thing". The operator's expertise should be the bottleneck, not the report's
  vocabulary. Explain *why* exhaustively; summarize *what* faithfully.
- Present the strongest version of each option, steelmanned, not strawmen set up to
  make the recommendation obvious.

### 5.6 Minimizing the human's verification workload

The agent bears the cost of making review cheap:

- **Isolate changes.** Separate commits for mechanical churn (renames, formatting,
  package moves) vs. behavior changes, and say which commits are which. The human's
  scarce attention should go to behavior diffs, not re-reading renamed code.
- **Call out hard-to-review hotspots.** Concurrency and ordering, time/timezone/DST
  arithmetic, persistence and migration, permission flows, null-handling fan-out, and
  diff hunks where moved code resembles changed code. For each hotspot: why it's
  risky, what to look at, and what evidence the agent can offer beyond "looks right".
- **Pre-verify edge cases yourself.** Before asking the human to weigh in on
  edge-case behavior, demonstrate it: a targeted unit test that pins the behavior, or
  a scratch run whose inputs and outputs are shown. The human should never have to
  construct test inputs by hand just to see what the code does.
- **Suggest a review order.** Tell the human where a limited review budget is best
  spent ("if you check only one thing, check the FreshStartCalendar DST boundary in
  commit X").
- **Give exact pointers, not areas.** `FreshStartCalendar.kt:87-103`, not "around the
  middle of the calendar file". Quote the minimum sufficient context inline so many
  questions are answerable without leaving the report.
- **For UI changes,** provide build-and-navigate steps (menu path, taps, observable
  outcome) so behavior can be checked on-device even when screenshots aren't
  available in the session.
- **Offer to split.** If the report reveals the change is bigger than one review can
  comfortably hold, propose splitting the branch before the push rather than after.

### 5.7 Anti-patterns (automatic grounds for rejection)

An agent doing any of the following has failed the review process, regardless of
whether the underlying code is good:

- Claiming tests or builds pass without a verbatim command, real output, and exit
  code.
- Presenting CI-corroborated status for runs that only happened locally, or claiming
  CI ran when it did not.
- Describing a diff in prose instead of showing it, or summarizing what a file now
  contains without quoting it.
- Presenting a completed checklist without per-item evidence.
- "I considered alternatives" without naming any.
- Omitting an edge case, deviation, or failure the agent knew about.
- Pushing, or committing, past a gate without explicit approval.
- Deciding a decision-shaped question alone when the human was available to ask
  (conversely: padding the gate queue with logging-tier minutiae, per §5.1.1).
- Treating human delegation ("use your judgment") as permanent or transferable to
  later decisions.
- Applying social pressure to the operator ("this is probably fine to push",
  repeated re-asking after a rejection). The gates exist to be used.
- Manufacturing questions or edge cases to appear thorough. Depth of search is the
  requirement, not volume of output.

---

## 7. Multi-Agent Orchestration Protocol

When work is coordinated across multiple agent workers (parallel waves, integration
branches, gatekeeper reviews), the rules in this section layer on top of §1–§5. They
exist because parallel agents multiply both throughput and failure modes: a protocol
that works for one careful agent is not sufficient for eight concurrent ones.
### 7.1 Worker specifications

Every dispatched worker receives a self-contained spec that includes:

- **Binding context:** which docs to read first (this file, DEVELOPMENT.md, relevant
  design docs), the Java/SDK setup commands, and the exact milestone or task text.
- **Anti-idle block (verbatim, mandatory):** workers must work continuously, resume
  immediately if a turn ends prematurely, send `worker_done` only when acceptance
  criteria are met, and escalate rather than stall. Terse specs cause idle-at-prompt
  stalls; this block is not optional.
- **Explicit authorization scope:** what the worker may decide alone (§5.1.1 logging
  tier) versus what must be escalated. Mid-task permission requests for already-
  authorized work are over-caution and slow the wave; the spec should pre-authorize
  everything within the milestone's acceptance criteria.
- **Hard constraints:** local-first rules (§1, §2), conventional commits, feature
  branch naming, no pushes by workers unless their spec says otherwise.

### 7.2 Parallelism and build guardrails

- Fill capacity: decompose independent milestones into parallel workers. Validation,
  review, docs, and planning tasks ride alongside code tasks.
- **At most one heavyweight Gradle build runs per machine at a time.** Workers queue
  behind it or do non-build work first. Two concurrent `gradlew` invocations on the
  same project corrupt caches and produce phantom failures.
- Integration is a separate step from authorship: worker branches merge onto an
  integration branch by the coordinator, and **merge collisions are expected** —
  code that compiled in isolation collides at integration (duplicate helpers,
  incompatible API shapes, test-fixture drift). The coordinator resolves them, then
  runs the full build+test gate before anything else proceeds (§4).
- Workers write tests that pass against *their* understanding; integration must
  re-verify the *combined* semantics (see: per-channel counting bugs that only
  manifest when two features share a ledger).

### 7.3 Standing approvals (scope-limited)

The human may grant standing pre-approval for repetitive, low-risk operations:

- **Push + PR creation** after a gatekeeper-approved, build-green integration. This
  satisfies §5.1's pre-push gate for branch pushes and PR opens only.
- **Merging into `main` is never covered by standing approval.** Every merge needs
  the human's explicit action or instruction on that specific PR.
- Standing approvals are recorded in this file with date and scope, and can be
  revoked at any time. If any material fact changes (e.g., a gatekeeper verdict
  reversal), re-present before using the approval again.

### 7.4 Gatekeeper review (pre-human gate)

Before a PR reaches the human, an independent reviewer agent on a stronger model
  than the workers:

- Re-runs the build and test suite itself; does not trust the coordinator's claims
  (§5.4 applies recursively — verifier independence matters).
- Reviews correctness, security, and this file's compliance (especially §1, §2).
- Posts its verdict on the PR (approve / request-changes) with findings classified
  blocker / major / minor.
- Blockers must be fixed and re-verified before the PR is presented to the human.
  Majors are fixed or explicitly deferred with the human's visibility in the PR
  body. The gatekeeper cannot approve its own work; it reviews, the coordinator
  fixes.

The human only sees gatekeeper-approved PRs. This is not to substitute the human's
judgment (§5 stands) but to ensure that the judgment is spent on genuine merits,
not on catching mechanical defects.

### 7.5 Stall handling and supervision

- Workers stall in recognizable patterns: idle-at-prompt (terse spec), malformed
  tool-call emissions (mid-generation termination), or finished-but-unreported.
  Supervision distinguishes these by checking git activity and dispatch state, not
  terminal quietness alone — **a quiet terminal with a fresh commit is done, not
  stalled.** Abandon-and-redispatch only after nudges fail AND no progress evidence
  exists.
- A redispatched worker inherits the same spec (with anti-idle block) on a fresh
  terminal. Work-in-progress from the abandoned attempt is recovered via git stash
  or branch salvage before the retry starts.
- Orchestration config and post-mortem lessons live outside the repo (agent skill
  files); this section is the repo-side contract those tools implement.

---

## 8. Review Checklist (run through before every PR)

- [ ] No secrets, personal data, or proprietary material anywhere in the diff
      (double-check added files, not just edited ones).
- [ ] `.gitignore` updated if new config or credential file types were introduced.
- [ ] Nudges are user-initiated, reversible, and explained; no dark patterns; no
      manipulation of third parties.
- [ ] No analytics, tracking, or ad SDKs added.
- [ ] Permission additions are justified and minimized.
- [ ] Builds cleanly; tests added and passing in CI, or limitations stated plainly.
- [ ] Strings externalized; minSdk respected.
- [ ] Commit message and PR description follow conventions; docs updated.
- [ ] Nothing references private information about anyone, including from outside the
      repo (issue text, screenshots, fixture data).
- [ ] Change report (§5.3) presented with per-item evidence for each box above; a
      bare checkmark is not a completed checklist.
- [ ] If multi-agent work (§7): integration build+test re-run on the merged branch,
      gatekeeper verdict posted, and merge-collision fixes documented with root
      causes (not just "fixed compile errors").
- [ ] Randomly spot-check at least two quoted excerpts against the repo before
      approving. (This is the human's one standing duty; everything above exists to
      make it sufficient.)

When in doubt about whether something belongs in a public commit: **it doesn't.**
Ask in an issue instead.
