# 04 — The Combined Timeline

> How the merged view is computed, how contention is detected and shown, and how the owner resolves
> it. Implements [`01_REQUIREMENTS_AND_RULES.md` §3](01_REQUIREMENTS_AND_RULES.md).
>
> Nothing in this document exists in code yet.

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

### 2.1 Segmentation

Collect every start and end across all devices into a sorted boundary list; each adjacent pair is
an atomic segment during which the set of active devices is constant. This is what makes R6
mechanically enforceable — attribution is decided **per segment**, and segments never overlap, so
totals cannot double-count by construction.

### 2.2 Classification

A segment is **contended** when two or more devices are simultaneously non-idle in it.

Idle must be excluded before this test, or every locked phone in a pocket contends with everything.
A device is idle when the screen is off, or when it reports an explicit AFK/idle state, or when it
has no input for the idle threshold.

> **Minimum duration — settled at 60 seconds (D15).** Contention shorter than a minute attaches to
> the neighbouring settled segment instead of becoming a question. Without this, walking between two
> devices generates dozens of meaningless prompts a day and the shading in R8 stops meaning
> anything. Exposed as a setting so it can be tuned against real usage.

### 2.3 Applying decisions

Two passes, in this order:

1. **Exact** — a decision recorded for this specific window (`scope: once`).
2. **Rule** — an `always` decision whose *signature* matches this segment (R14/R16).

Exact always beats rule, so a one-off correction can override a standing rule without deleting it.

A segment resolved by a rule is marked `auto_resolved` with the id of the originating decision, so
the UI can show *"resolved by your rule: YouTube + Kindle → reading"* and offer one-tap revocation
(R16).

### 2.4 Provisional attribution

Unresolved contention still needs a number today (**R17**). The placeholder:

1. Longest total duration in the segment wins.
2. Tie → lowest device UUID, lexicographically.

Step 2 is not arbitrary — it is what makes the result **identical on every device** (R18). Any
tiebreak involving local clock, sync order or "most recent" would break that and must be rejected.

The segment stays flagged `unresolved` so it still renders shaded (R8). Provisional attribution
changes the number; it never clears the shading.

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
