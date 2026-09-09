# 04 — The Combined Timeline

> How the merged view is computed, how contention is detected and shown, and how the owner resolves
> it. Implements [`01_REQUIREMENTS_AND_RULES.md` §3](01_REQUIREMENTS_AND_RULES.md).
>
> Pipeline steps ①–③ landed in code in roadmap 3.2 and ⑤–⑥ in 3.3 — the `aw-combined` crate in
> `aw-server-rust` (`compute_segments`, modules `normalise` / `segment` / `classify` / `attribute`,
> plus the separate opt-in `coalesce`). **Step ④ (apply decisions) is Phase 4 and is not built**;
> `compute_segments` runs ①②③⑤ and leaves the gap for ④. The resolution UI (§3 onward) is not built
> yet. Annotations below mark what maps to code.

---

## 1. The shape of the view

```
┌──────────────────────────────────────────────────────────────┐
│  COMBINED          ▓▓▓▓▓▓▓▓░░░░░░░░████████▒▒▒▒▒▒▒▒▓▓▓▓▓▓▓▓  │  ← what I was doing
│                            ↑ shaded = unresolved contention   │
├──────────────────────────────────────────────────────────────┤
│  📱 phone          ▓▓▓▓▓▓▓▓        ████████        ▓▓▓▓▓▓▓▓  │
│  📱 tablet                 ░░░░░░░░████████▒▒▒▒▒▒▒▒          │
│  💻 laptop                         ████████                  │
└──────────────────────────────────────────────────────────────┘
```

The combined track sits **above** the per-device tracks (the owner's words: *"a combined timeline
above them that will show everything"*). Per-device tracks are unmodified raw truth (R11) and are
always available underneath for comparison.

---

## 2. Computation pipeline

Pure and deterministic (**R18**) — same inputs, same output, on every device.

```
per-device events (all devices, after sync)
        │
        ▼
  ① normalise      → one flat list of intervals, each tagged with origin device
        │
        ▼
  ② segment        → cut at every boundary; produce non-overlapping atomic segments
        │
        ▼
  ③ classify       → segment with ≥2 active devices = CONTENTION; else SETTLED
        │
        ▼
  ④ apply decisions → exact-match decisions, then `always`-scope rules  (R16)
        │
        ▼
  ⑤ provisional    → any still-unresolved contention gets the placeholder pick (R17)
        │
        ▼
  ⑥ coalesce       → merge adjacent segments with identical attribution
        │
        ▼
  combined timeline: exactly one foreground per instant (R6) + background set + shaded flags
```

> **In code (3.3):** ⑤ is `aw-combined::attribute` — run by `compute_segments` right after ③,
> setting `Segment::foreground` and `Segment::unresolved`. ⑥ is `aw-combined::coalesce`, a
> **separate public function `compute_segments` does not call** — the pipeline stays lossless so
> Phase 4 can attach decisions to the atomic segments, and the view opts into coalescing. ④ is
> Phase 4; ⑤ runs whether or not ④ has.

### 2.1 Segmentation

Collect every start and end across all devices into a sorted boundary list; each adjacent pair is
an atomic segment during which the set of active devices is constant. This is what makes R6
mechanically enforceable — attribution is decided **per segment**, and segments never overlap, so
totals cannot double-count by construction.

> **In code (3.2):** `aw-combined::segment`. ① `aw-combined::normalise` builds the interval list
> first — resolving each event's origin device (the 3.1 `$aw.origin.device` tag, else the bucket's
> `-synced-from-<peer>` suffix, else the local UUID) and subtracting idle. Segments are **atomic**:
> a boundary from any device's app change is kept, and adjacent segments are never merged here —
> that is step ⑥ (`aw-combined::coalesce`, opt-in), keyed on identical *attribution* — the
> foreground device-and-`data` pair — not on device set.

### 2.2 Classification

A segment is **contended** when two or more devices are simultaneously non-idle in it.

Idle must be excluded before this test, or every locked phone in a pocket contends with everything.
A device is idle when the screen is off, or when it reports an explicit AFK/idle state, or when it
has no input for the idle threshold.

> **Minimum duration — settled at 60 seconds (D15).** Contention shorter than a minute attaches to
> the neighbouring settled segment instead of becoming a question. Without this, walking between two
> devices generates dozens of meaningless prompts a day and the shading in R8 stops meaning
> anything. Exposed as a setting so it can be tuned against real usage.

> **In code (3.2):** `aw-combined::classify`, `distinct_devices(seg) >= 2`. The 60 s minimum is
> implemented as **demote-and-flag over a contiguous contended *run***, not "attach to the
> neighbouring settled segment": if a maximal run of temporally adjacent contended segments totals
> < 60 s, every segment in it becomes `Settled` with `absorbed_short_contention = true`, keeping
> its slices. Runs, because a long contention is cut into many sub-60 s atomic segments by app
> changes and thresholding each one separately would erase real contention. "Attach to the
> neighbour" is undefined when both neighbours are settled with different activities or there is no
> settled neighbour; which activity a demoted segment's time is credited to is left to 3.3. Idle is
> an input the pipeline subtracts (`PipelineInput.idle`); on Android it is empty because
> `aw-watcher-android` only records while the screen is on and in use. `min_contention` is a
> parameter, not yet a user-facing setting.

### 2.3 Applying decisions

Two passes, in this order:

1. **Exact** — a decision recorded for this specific window (`scope: once`).
2. **Rule** — an `always` decision whose *signature* matches this segment (R14/R16).

Exact always beats rule, so a one-off correction can override a standing rule without deleting it.

A segment resolved by a rule is marked `auto_resolved` with the id of the originating decision, so
the UI can show *"resolved by your rule: YouTube + Kindle → reading"* and offer one-tap revocation
(R16).

### 2.4 Provisional attribution

> **In code (3.3):** `aw-combined::attribute`. `Segment::foreground` is an index into `active`;
> `Segment::foreground_slice()` / `background_slices()` are the accessors 3.4 calls.

Unresolved contention still needs a number today (**R17**). The placeholder:

1. **Longest-running activity wins** — and "duration" here means the length of the **originating
   activity interval** that covers the segment, *after idle subtraction*, **not** the segment's own
   length. Every slice in a segment covers that segment exactly (by construction in ②), so the
   segment's length is identical for every candidate and would make this rule dead code. What
   discriminates is that the phone's hour-long YouTube session outlasts the tablet's fifteen-minute
   Kindle dip. Post-idle, so a mostly-idle long event does not out-claim a shorter continuous one.
   In code each `ActiveSlice` carries `source_start` / `source_end` for exactly this comparison.
2. Tie → lowest device UUID, lexicographically.
3. Tie → lowest `bucket_id` (rule 2 cannot separate one device's two overlapping buckets).
4. Tie → lowest canonical `serde_json` string of `data` (`data` is not in ①'s sort key).

Steps 2–4 are not arbitrary — together they are a **total order**, which is what makes the result
**identical on every device** (R18). Any tiebreak involving local clock, sync order or "most
recent" would break that and must be rejected.

The segment stays flagged `unresolved` iff it is `Contended`, so it still renders shaded (R8).
Provisional attribution changes the number; it never clears the shading. A segment demoted to
`Settled` by the short-contention pass (§2.2) is **not** `unresolved` — never shaded, never asked
about — but it still gets the same provisional pick, so its time is credited to its longest-running
activity rather than lost.

---

## 3. The signature — why decisions generalise

**R14** requires storing *why* a decision applied. That is the **signature**: a canonical, sorted
description of what was competing.

```jsonc
"signature": {
  "participants": [
    { "device_role": "phone",  "app": "com.google.android.youtube", "category": "video" },
    { "device_role": "tablet", "app": "com.amazon.kindle",          "category": "reading" }
  ]
}
```

Properties that make it work as a rule key:

- **Sorted canonically** so `{phone:YT, tablet:Kindle}` and `{tablet:Kindle, phone:YT}` are one key.
- **Device *role*, not device UUID** — so "phone vs tablet" keeps meaning when a phone is replaced.
  The UUID is stored alongside for provenance but is not part of the match key.
- **Both app and category** are kept, so a rule can later be generalised from *this app* to *this
  category* without re-asking the owner.

> **This is the entire bridge from manual to automatic (R15).** Ship v1 storing signatures, and the
> rules engine becomes "group decisions by signature, offer the majority answer as a rule" — a
> feature built entirely from data already collected. Ship v1 *without* signatures and every
> decision ever made becomes worthless the day rules arrive.

---

## 4. Resolution UI

### 4.1 Entry points

- **Tap a shaded band** in the combined timeline (R9) — the primary path.
- **A "N unresolved" affordance** for the day, for batch review.

> Deliberately **not** a notification per contention. On three devices that would fire constantly
> and train the owner to dismiss it. Contention is reviewed when the owner chooses to look.

### 4.2 The sheet

```
┌─────────────────────────────────────────────┐
│  What were you doing?                       │
│  Today  14:30 – 14:45   ·   15 min          │
├─────────────────────────────────────────────┤
│  📱 phone                                   │
│     YouTube · video                 15 min  │
│                                             │
│  📱 tablet                                  │
│     Kindle · reading                12 min  │
├─────────────────────────────────────────────┤
│  ○ Reading            (tablet · Kindle)     │
│  ○ Watching YouTube   (phone)               │
│  ○ Something else…    → free-text label     │
│  ○ Neither — I was away                     │
├─────────────────────────────────────────────┤
│  Apply to:                                  │
│   ◉ Just this          ○ Always, when       │
│                          these two clash    │
├─────────────────────────────────────────────┤
│                      [ Cancel ]  [ Save ]   │
└─────────────────────────────────────────────┘
```

Notes:

- The four options map exactly to the outcomes in R11 — `foreground`, `relabel`, `ignore`, and
  `concurrent` (reachable by choosing a winner and marking the other deliberate).
- **"Apply to"** is where **R16** scope is chosen. It is one tap, present on every resolution, and
  it is what quietly accumulates the future rules engine.
- Saving writes one append-only line to *this* device's `decisions.jsonl` (R20) and recomputes the
  day locally. It syncs to the other devices on the next cycle (R26).

### 4.3 Undo

Every decision is reversible (**R12**). Undo appends a tombstone rather than editing history —
append-only files stay append-only (R20). Format in [`05_DATA_MODEL.md`](05_DATA_MODEL.md).

---

## 5. Where this renders

Two options, and this is a **real fork in the road** that should be decided before Phase 3:

| | **A — native Android view** | **B — modify aw-webui** |
|---|---|---|
| Effort | Medium | High |
| Reuse on desktop later (R2) | ✗ Android only | ✓ shared with all platforms |
| Interaction quality | Native, precise tap targets | Vue in a WebView |
| Risk | Duplicate work when desktop lands | Vue build pipeline inside CI |

Leaning **A for v1, B for v2**: a native view proves the model quickly on the device that exists
today, while the computation pipeline (§2) stays in shared Rust so B can reuse it unchanged. Tracked
as **Q4** in [`07_OPEN_QUESTIONS.md`](07_OPEN_QUESTIONS.md).

> **This choice now leans further toward A.** `aw-webui` is desktop-first and needs its own mobile
> work regardless ([`02`](02_ARCHITECTURE.md) §7.2, Phase 5). Option B would mean adding brand-new
> screens to a codebase that is already the source of the layout problem — so decide Q4 together
> with **Q8**, not separately.

Whichever is chosen, these screens are **designed at phone width first** (**R33**). They must not
inherit the problem Phase 5 exists to fix.

> Keeping the pipeline in Rust rather than Kotlin is what makes this reversible. If it is written in
> Kotlin, choosing B later means writing it a second time.

---

## 6. Worked example

Raw:

| Device | Activity | Window |
|---|---|---|
| phone | YouTube | 14:00 – 15:00 |
| tablet | Kindle | 14:30 – 14:45 |

Segments: `14:00–14:30` (phone only), `14:30–14:45` (**both**), `14:45–15:00` (phone only).

| Segment | State | Foreground | Background | Counts |
|---|---|---|---|---|
| 14:00–14:30 | settled | YouTube | — | 30 min YouTube |
| 14:30–14:45 | **contended → shaded** | *provisional:* YouTube | Kindle | 15 min |
| 14:45–15:00 | settled | YouTube | — | 30 min YouTube |

Owner taps the shaded band, picks **Reading**, scope **always**:

| Segment | State | Foreground | Background | Counts |
|---|---|---|---|---|
| 14:30–14:45 | resolved | **Kindle / reading** | YouTube | 15 min reading |

Day total: **60 minutes** — matching wall-clock, before and after (**R6**). The decision's signature
`{phone:YouTube, tablet:Kindle}` now auto-resolves this pairing forever, revocably.
