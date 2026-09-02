# 07 — Open Questions

> Unsettled decisions. When one is settled, record it in the Decision Log in
> [`README.md`](README.md), update the affected doc, and mark the row **RESOLVED** here — never
> delete it, so the reasoning survives.

---

## Q1 — Minimum contention duration ✅ RESOLVED (2026-09-02) — **60 seconds**

Contention shorter than **60 seconds** is absorbed into the neighbouring settled segment and never
shaded or surfaced for resolution.

Walking from phone to tablet creates seconds-long overlaps; shading each one would produce dozens of
meaningless prompts a day and train the owner to ignore shading entirely — which would defeat R8.

Owner's call, taken as a starting value rather than a final one: **expose it as a setting** and
revisit once there is real usage. If a minute turns out to swallow genuine short switches, lower it.

**Implement in:** Phase 3.2.

---

## Q2 — Can `concurrent` split time proportionally? ✅ RESOLVED — no

**R6** forbids it. Wall-clock conservation is the invariant that makes the combined timeline
trustworthy; proportional splitting reintroduces double-counting through the back door.

`concurrent` is expressed as one foreground plus a *deliberate* background marker, which preserves
the information ("I was doing both") without inflating totals.

---

## Q3 — Do retired devices keep their history? ⬜ *leaning yes*

**Blocks:** nothing yet; decide before Phase 5.

The time genuinely happened, so deleting it would falsify past days. Leaning **keep**, with the
device marked inactive and hidden from the per-device track list once stale.

Sub-question: how long before a device is presumed retired? `meta.json.last_seen` makes this
detectable; the threshold is a product call.

---

## Q4 — Native view or aw-webui for the combined timeline? ⬜ *leaning native for v1*

**Blocks:** Phase 3.4 — decide before starting it.

Full comparison in [`04_COMBINED_TIMELINE.md` §5](04_COMBINED_TIMELINE.md).

Leaning **native for v1, aw-webui for v2**. The cost of being wrong is contained *provided* the
computation pipeline lives in Rust (Phase 3.2) — then switching renderers rewrites only the view. If
the pipeline is written in Kotlin instead, this decision becomes expensive to reverse, which is why
3.2 specifies Rust.

---

## Q5 — Idle detection quality on Android ⬜ *engineering call — not the owner's*

**Blocks:** Phase 3.2 — contention classification depends on it.

**What "idle" means here, plainly:** the device is powered on, but nobody is using it — phone
face-down on the desk, tablet left propped up after you walked away.

**Why it matters:** contention is "two devices active at once" (**R7**). If a device that is merely
*awake* counts as *active*, then a phone sitting untouched in a pocket contends with whatever you
are genuinely using, and the day fills with shaded bands that have no real question behind them.
Idle detection is what keeps contention meaning *"two things were really happening"*.

**Available signals:** screen-off state, the AFK/idle watcher, and a no-input timeout. How reliable
each is on modern Android — with aggressive background restrictions and OEM battery managers — is
unknown and needs measuring rather than guessing.

**Resolve during Phase 1**, while a device is already in hand and instrumented for sync
verification. Pick the most reliable available signal, document what it actually does, and move on;
this is an implementation detail, not a product decision.

---

## Q6 — Sync interval ⬜ *default 15 min*

Currently 15 minutes. Shorter feels more responsive but costs battery; Syncthing already skips
unchanged files, so the real cost is the app's own push/merge work rather than transfer.

Revisit once Phase 1 shows what a cycle actually costs. Not worth deciding on speculation.

---

## Q8 — How far does the mobile UI work go? ⬜ *decide after Roadmap 1.6 + 5.1*

**Blocks:** Phase 5.2.

`aw-webui` is desktop-first ([`02`](02_ARCHITECTURE.md) §7.2). Once the cheap WebView viewport fix
(Roadmap 1.6) has landed and the actual breakages are listed (5.1), pick a route:

| | Approach | Cost | Notes |
|---|---|---|---|
| **A** | Inject a mobile stylesheet from the WebView | Low | No upstream changes, no fork divergence. A hack, but a contained one. |
| **B** | Patch `aw-webui`'s own CSS in the fork | Medium | Benefits desktop too; adds fork divergence to maintain against upstream. |
| **C** | Native screens for the views that matter | High | Best result on phone; duplicates work that already exists in Vue. |

**Do not decide this in advance.** The right answer depends entirely on how much survives 1.6 — if
the viewport fix alone gets the UI to "functional" (**R34**), C is unjustifiable.

Note the interaction with **Q4**: if `aw-webui` needs heavy mobile work anyway, that argues for
building the *new* combined-timeline screens natively and mobile-first (**R33**) rather than adding
them to a desktop-shaped codebase.

---

## Q7 — What happens when two devices report the same hostname? ⬜

**Blocks:** nothing — UUIDs make it safe — but it is a **UI** problem.

Hostname derives from the device name, and two phones can both be `"phone"`. Post-Phase-1 the UUID
keeps their data separate and correct, but the timeline would show two indistinguishable tracks both
labelled "phone".

`meta.json.display_name` is shared (**R25**) precisely so this is fixable — but nothing currently
prompts the owner to set a distinct name. Suggested: detect the collision on sync and prompt once.
