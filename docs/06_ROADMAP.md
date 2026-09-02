# 06 — Roadmap

> **👉 START HERE:** Phase 1, Step 1.1. Sync is dead and nothing else can be built or even tested
> until it works. Do not start the combined timeline first — it has no multi-device data to operate
> on and no way to prove it is right.

**How to work this document:** do one step, run its check, stop. Then update the step in place —
mark it `✅ DONE (date)`, write a **Result** saying what is *actually true now*, and flag with ⚠️
anything not verified. Append to the Progress Log at the bottom, newest first.

---

## Phase 1 — Make sync work *(blocking everything)*

Fixes the four blockers in [`03_SYNC.md` §2](03_SYNC.md). Nothing here is new functionality; it is
what has to be true before any feature exists.

### 1.1 — Unique device identity ⬜
Replace `SyncInterface.getDeviceId()` with a `UUID.randomUUID()` minted once and persisted in
`AWPreferences` (`device_uuid`). Add the restore guard from [`05`](05_DATA_MODEL.md) §7.
**Check:** two devices report two different UUIDs in logcat. *(R22)*

### 1.2 — Fix the push/pull depth mismatch ⬜
In `sync_wrapper.rs`, drop the `_staging` level — push to `<sync>/<hostname>/` and let
`setup_local_remote` create `<device_id>/`. Delete the unused `device_id` local in
`pull_from_hostname`.
**Check:** after a sync, `<sync>/<host>/<uuid>/test.db` exists at exactly that depth, and
`get_remotes()` returns a non-empty list.

### 1.3 — Pull every database, never the largest ⬜
Remove the `max_by_key(len)` selection; iterate all discovered dbs.
**Check:** no `"choosing largest db"` in logs with three dbs present. *(R19)*

### 1.4 — Bidirectional SAF mirror ⬜ ← *the important one*
Add the import pass: SAF `devices/<other>/` → app-private staging, for **every directory except
our own**. Keep export restricted to our own directory. Copy before opening. *(R21, R24)*
**Check:** peer `.db` files appear in app-private storage after a sync.

### 1.5 — Two-device end-to-end verification ⬜
Run the full procedure in [`03_SYNC.md` §5](03_SYNC.md).
**Check:** device A displays a bucket only device B could have produced.

> **Phase 1 is not done until 1.5 passes on real hardware.** Everything downstream assumes
> cross-device data actually arrives; a green build proves nothing here.

### 1.6 — WebView viewport quick fix ⬜ *(independent of the rest of Phase 1)*
In `WebUIFragment.onCreateView`, alongside the existing two settings:

```kotlin
settings.useWideViewPort = true        // honour the page's <meta viewport> — currently ignored
settings.loadWithOverviewMode = true   // fit the page to the screen on load
settings.setSupportZoom(true)          // escape hatch for anything still cut off
settings.builtInZoomControls = true
settings.displayZoomControls = false   // pinch-zoom, but no ugly on-screen +/− buttons
```

Rationale in [`02_ARCHITECTURE.md` §7.1](02_ARCHITECTURE.md) — `useWideViewPort` defaults to
`false`, so `aw-webui`'s perfectly good mobile viewport tag is being discarded today.
*(R32)*

**Check:** the dashboard fits the screen width on load, and anything still oversized can at least be
reached by pinch-zooming.

> **Sequenced here deliberately.** It is a handful of lines, it is independent of the sync work, and
> step 1.5 requires *reading data off the screen on two phones* — which is materially easier if the
> UI is not cut off. It also measures how much of the UI problem is Layer 1 versus Layer 2 before
> committing effort to Phase 5.

---

## Phase 2 — Shared state

### 2.1 — Shared folder layout + `VERSION` ⬜
Create `devices/<uuid>/`, write `meta.json`, add version read/refuse. *(R20)*

### 2.2 — Append-only JSONL store ⬜
Read/write/merge for `decisions.jsonl` and `settings.jsonl`, including tombstones and the
deterministic tiebreak from [`05`](05_DATA_MODEL.md) §4.2. **Unit-test the merge with shuffled
input orders** — the property that matters is order-independence (R18), and it is easy to lose
without a test that specifically looks for it.

### 2.3 — Settings sync ⬜
Route `category.*` / `label.*` through the shared store; keep device-local keys in `AWPreferences`.
**Check:** rename YouTube to "fun" on device A; device B shows "fun" after a sync. *(R25, R28)*

---

## Phase 3 — Combined timeline (read-only)

### 3.1 — Origin tagging at merge ⬜
Tag imported events with their source device UUID. *(R11 — raw data untouched)*

### 3.2 — Segmentation + classification ⬜
Implement pipeline steps ①–③ from [`04`](04_COMBINED_TIMELINE.md) §2, **in Rust** so a future
desktop client and a future aw-webui view reuse it (R2, Q4). Include idle exclusion and the
minimum-duration threshold (Q1).
**Check:** golden tests — known event sets produce known segments. Include a three-device case; a
two-device-only implementation will pass a two-device test and still violate **R1**.

### 3.3 — Provisional attribution ⬜
Pipeline steps ⑤–⑥ with the deterministic tiebreak. *(R17, R18)*
**Check:** the same input yields identical output across repeated runs and across devices; totals
equal wall-clock (**R6**) — assert this directly, it is the invariant everything else rests on.

### 3.4 — Combined view with shading ⬜
Render the combined track above per-device tracks; shade unresolved contention. *(R8)*
Decide Q4 (native vs aw-webui) before starting.

---

## Phase 4 — Manual resolution

### 4.1 — Resolution sheet ⬜
Tap-to-open, the four outcomes, and the `once` / `always` scope control.
*(R9, R11, R16)*

### 4.2 — Persist + apply decisions ⬜
Write to `decisions.jsonl`; apply exact matches, then signature rules; mark `auto_resolved`.
**Check:** resolve on A → after sync, B shows the same resolution and no longer asks. *(R26)*

### 4.3 — Undo ⬜
Tombstones; segment returns to shaded. *(R12)*

> **End of Phase 4 = the product the owner asked for.** Everything after this is convenience.

---

## Phase 5 — Make the UI usable on a phone

Layer 2 from [`02_ARCHITECTURE.md` §7.2](02_ARCHITECTURE.md) — the part 1.6 cannot fix.
Target is **functional, not beautiful** (**R34**): nothing cut off, nothing unreachable, every
control tappable in portrait with one thumb.

> **Do 1.6 first and re-assess.** How much remains after the viewport fix determines how much of
> this phase is actually needed, and answers **Q8**. Do not scope this phase before that is known.

### 5.1 — Audit what actually breaks ⬜
On a real phone in portrait, list every screen that overflows, with the offending element. Charts,
tables and the navbar are the expected culprits.
**Check:** a written list of concrete breakages, not an impression. This is the input to Q8.

### 5.2 — Decide Q8 ⬜
Patch `aw-webui`'s CSS, inject a mobile stylesheet from the WebView, or build native screens for the
views that matter. Decide with 5.1's list in hand.

### 5.3 — Kill horizontal page scroll ⬜
The page must not scroll sideways; wide tables and charts scroll inside their own containers
instead. *(R31)*
**Check:** no screen scrolls the page horizontally in portrait.

### 5.4 — Touch targets and navigation ⬜
Controls sized for a thumb, not a mouse. Includes the long-standing gap that there is **no reliable
way to reach Sync settings** — the navigation drawer is unreliable on modern Android.
**Check:** every action reachable one-handed in portrait.

> **New screens are exempt from this phase — they must be born mobile-first (R33).** The combined
> timeline (Phase 3.4) and resolution sheet (Phase 4.1) are designed at phone width from the start,
> so they never join the backlog this phase exists to clear.

---

## Phase 6 — Later

- Rules engine proper — generalise accumulated signatures (**R15**; the data is already there).
- Device role priors (screen-off is never foreground).
- Desktop client (**R2**).
- aw-webui combined view (Q4 option B).
- Suggested resolutions from decision history.
- Visual design pass, once functional is settled (**R34**).

---

## Upstream maintenance

Both repos are forks of active projects.

**Weekly:** check upstream PRs/issues on `ActivityWatch/aw-android` and
`ActivityWatch/aw-server-rust` for anything touching `aw-sync/` or `SyncInterface.kt`.

**Merging:** always onto a feature branch first, never straight to `beta`.

```bash
git fetch ActivityWatch master
git diff beta..ActivityWatch/master --stat -- aw-sync/ aw-server/
git checkout -b sync-upstream-$(date +%Y%m%d)
git merge ActivityWatch/master
```

| Upstream changed | Action |
|---|---|
| Files we don't touch (CI, widget, notify) | Merge freely |
| `aw-sync/` internals | Manual review — our fixes must survive |
| Their own sync/conflict work | Read carefully; may supersede ours |
| `SyncInterface.kt` / `build.yml` | Manual — verify JNI symbols still line up |

After any Rust merge: update the submodule pointer, push, rebuild in Actions
([`02`](02_ARCHITECTURE.md) §5).

---

## Progress log

*Newest first.*

### 2026-09-02 — Mobile UI added to scope; Q1 and Q5 settled
Owner reported the UI is built for a PC — it scrolls sideways and content is cut off. Added as
rules **R30–R34** with the root cause in [`02`](02_ARCHITECTURE.md) §7 and the work as Phase 5.

Found a cheap partial fix while investigating: `aw-webui` **already declares a correct mobile
viewport**, but `WebUIFragment` never sets `useWideViewPort`, which defaults to `false` — so the
WebView **ignores it**. Zoom is disabled too, making cut-off content unreachable rather than merely
awkward. Sequenced as step **1.6**, ahead of the UI phase, because it is a few lines, is independent
of the sync work, and reveals how much of the problem is anything deeper.

- **Q1 resolved:** contention under **60 seconds** is ignored, exposed as a setting (D15).
- **Q5 reframed:** idle detection is an engineering call, not the owner's — resolve it during
  Phase 1 while a device is instrumented.

### 2026-09-02 — Docs restructured; sync root-caused
Replaced `MULTI_DEVICE_SYNC_DESIGN.md` and `fix_hostname_migration.md` with this `docs/` set.
Read the sync path end to end and found **four independent blockers**, all documented in
[`03_SYNC.md`](03_SYNC.md): one-way SAF mirror, push/pull depth mismatch, non-unique device IDs,
and largest-db-wins in pull. The one-way mirror is the architectural one — it alone makes
cross-device sync impossible regardless of the others.

Corrections to the previous plan:
- Its per-device `_staging` directory **caused** the depth mismatch rather than solving anything.
- Its single shared `user_decisions.json` would have produced continuous Syncthing conflict copies;
  replaced with append-only per-device logs (**R20**).
- Its "close/reopen all datastore connections" step is unnecessary — copying imported databases to
  app-private storage before opening solves the same problem without new Rust lifecycle APIs.
- `fix_hostname_migration.md` chased hostname migration as the cause of the missing `.db`; it was
  not. Blockers 1 and 2 fully explain that symptom. Thread closed.

⚠️ All four blockers are **read from source, not yet reproduced on device.** Phase 1 verifies them.

### 2026-09-01 — JNI panic-catching landed
`catch_unwind` + `android_logger` in `android.rs`; Kotlin calls `awSyncInitLogging(2)`. Rust panics
now return an error JSON instead of `SIGABRT`. Real fix, but it exposed rather than resolved the
blockers above.

### 2026-08-31 — `UnsatisfiedLinkError` root-caused
Cause was the CI cache trap ([`02`](02_ARCHITECTURE.md) §5): uncommitted Rust changes → unchanged
cache key → stale `.so` in the APK. Fixed by forking `aw-server-rust` and pointing the submodule at
the fork's `beta`.

### 2026-08-30 — First multi-device attempt
Per-device push + multi-hostname pull written across five files. Never worked on device; superseded
by Phase 1.
