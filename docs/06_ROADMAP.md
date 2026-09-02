# 06 — Roadmap

> **👉 START HERE:** **Run a CI build.** Steps 1.0–1.3 and 1.6 are written but have never been
> compiled, let alone run — a local build is not possible (R4), so CI is the first check any of
> them get. Push `aw-server-rust@beta` **first** ([`02`](02_ARCHITECTURE.md) §5, cache trap), then
> the submodule pointer, then build. After that: **1.4**, then **1.5**.
>
> Sync is dead and nothing else can be built or even tested until it works. Do not start the
> combined timeline first — it has no multi-device data to operate on and no way to prove it is
> right.

**How to work this document:** do one step, run its check, stop. Then update the step in place —
mark it `✅ DONE (date)`, write a **Result** saying what is *actually true now*, and flag with ⚠️
anything not verified. Append to the Progress Log at the bottom, newest first.

---

## Phase 1 — Make sync work *(blocking everything)*

Fixes the blockers in [`03_SYNC.md` §2](03_SYNC.md). Nothing here is new functionality; it is what
has to be true before any feature exists.

> **Status 2026-09-02:** 1.0–1.3 and 1.6 are written and now **compile-checked locally**, except
> `android.rs` — see [`02`](02_ARCHITECTURE.md) §5.1 and **D22**. Nothing has run on a device.
> Next: a CI build (which is the only check `android.rs` gets), then 1.4, then 1.5.
>
> | Step | Compiles | How |
> |---|---|---|
> | 1.0 | ❌ not checked | `android.rs` — CI only |
> | 1.1 | ⚠️ Kotlin only | `resolveDeviceId()` ✅; its JNI counterpart in `android.rs` ❌ |
> | 1.2 | ✅ | `cargo check` host |
> | 1.3 | ✅ | `cargo check` host |
> | 1.6 | ✅ | `compileDebugKotlin` |

### 1.0 — Forward the API key from the JNI client ✅ DONE 2026-09-02 ⚠️ *unverified*
Blocker 5, found while checking upstream drift and **upstream of all the others** — without it push
obtains no bucket data at all, so no `.db` can appear whatever the layout is.

In `aw-sync/src/android.rs::get_client()`: call `apply_android_data_dir_from_env()` (ported from
upstream, recovers filesDir from `XDG_DATA_HOME`; no new JNI symbol), then build the client with
`AwClient::new_with_api_key()` using `util::get_server_config()`.

**Result:** `get_client` now sets the android data dir and forwards `[auth].api_key`.
⚠️ **Not compile-checked** — `android.rs` needs the android target (D22); CI is its first check. **Check:** no `401` from `GET /api/0/buckets` in logcat during a sync, and
`using API key from config.toml for local client` appears at info level.

> **Do not cherry-pick `aw-android#249`** to get this — see the warning in
> [`03_SYNC.md` §2.5](03_SYNC.md).

### 1.1 — Unique device identity ✅ DONE 2026-09-02 ⚠️ *unverified*
**Rewritten from the original step** — see the correction box in
[`03_SYNC.md` §2.3](03_SYNC.md). `aw-server` already mints a persisted `Uuid::new_v4()`, and it is
that id — not Kotlin's — that names the device directory. So rather than minting a second one:

- `aw-sync/src/android.rs` exports `SyncInterface.getDeviceId(port)`, returning
  `client.get_info().device_id`.
- `SyncInterface.kt` replaces the old installer-package/`Build.FINGERPRINT` hash with
  `resolveDeviceId()`, which calls that and caches the result. Null means *not known yet*, never
  *mint a new one*.

**Result:** one identity across Kotlin and Rust, matching the `.db` path already on disk.
⚠️ Kotlin side compiles; the `android.rs` JNI export is **not compile-checked** (D22). Not run. **Check:** two devices log two different UUIDs, and each matches its own
directory name under `<sync>/<hostname>/`. *(R22)*

> **Still owed:** the restore guard from [`05`](05_DATA_MODEL.md) §7. It now applies to
> aw-server's `device_id` file, which an app backup clones just as readily. Do it in Phase 2 with
> `meta.json`, which is what the guard compares against.

### 1.2 — Fix the push/pull depth mismatch ✅ DONE 2026-09-02 ⚠️ *unverified*
Dropped the `_staging` level in `push_with_hostname_and_device_id`; it now pushes to
`<sync>/<hostname>/` and lets `setup_local_remote` create `<device_id>/`. The `device_id` argument
is now log-only (documented in place).

**Result:** push and `get_remotes()` agree on `./{host}/{device_id}/*.db`.
✅ Compiles (`cargo check` host). Not run. **Check:** after a sync, `<sync>/<host>/<uuid>/test.db` exists at exactly that
depth, and `get_remotes()` returns a non-empty list.

### 1.3 — Pull every database, never the largest ✅ DONE 2026-09-02 ⚠️ *unverified*
Replaced `max_by_key(len)` in `sync_wrapper.rs::pull()` with iteration over all discovered dbs, and
deleted the unused `device_id` local in `pull_from_hostname`.

**Result:** no path selects a single db by size.
✅ Compiles (`cargo check` host); `sync_wrapper.rs` is now warning-free. Not run. **Note:** this was **half-satisfied already** — Android's multi-device path is
`pull_all_from_all_hostnames` → `pull_from_hostname`, which already iterated every db. The
`max_by_key` was only on the legacy `pull()` path reached via `syncPullAll`. Blocker 4 was
therefore real but not on the live path, which lowers its share of the original symptom.
**Check:** no `"choosing largest db"` in logs with three dbs present. *(R19)*

### 1.4 — Bidirectional SAF mirror ⬜ ← *the important one*
Add the import pass: SAF `devices/<other>/` → app-private staging, for **every directory except
our own**. Keep export restricted to our own directory. Copy before opening. *(R21, R24)*
Use `resolveDeviceId()` from 1.1 to decide which directory is ours.
**Check:** peer `.db` files appear in app-private storage after a sync.

### 1.5 — Two-device end-to-end verification ⬜
Run the full procedure in [`03_SYNC.md` §5](03_SYNC.md).
**Check:** device A displays a bucket only device B could have produced.

> **Phase 1 is not done until 1.5 passes on real hardware.** Everything downstream assumes
> cross-device data actually arrives; a green build proves nothing here. Five blockers have now
> been found by reading source, and the fifth was found only because upstream had already hit it —
> so do not treat the list as closed until 1.5 is green.

### 1.6 — WebView viewport quick fix ✅ DONE 2026-09-02 ⚠️ *unverified*
Added to `WebUIFragment.onCreateView`: `useWideViewPort`, `loadWithOverviewMode`, `setSupportZoom`,
`builtInZoomControls`, and `displayZoomControls = false`.

Rationale in [`02_ARCHITECTURE.md` §7.1](02_ARCHITECTURE.md) — `useWideViewPort` defaults to
`false`, so `aw-webui`'s perfectly good mobile viewport tag was being discarded. *(R32)*

**Result:** the WebView now honours the page's viewport and permits pinch-zoom.
✅ Compiles (`compileDebugKotlin`). Not run. **Check:** the dashboard fits the screen width on load, and anything still
oversized can at least be reached by pinch-zooming. **Record what remains** — that list is the
input to Q8 and decides how large Phase 5 is.

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

Both repos are forks of active projects. **This is not optional overhead — it found Blocker 5.**
Upstream had already hit the same 401 and fixed it; reading their commits was cheaper than another
device-debug cycle would have been.

> **One-time setup (done 2026-09-02).** Neither clone had an `ActivityWatch` remote, so the
> commands below could not run as written. Added in both repos:
> ```bash
> git remote add ActivityWatch https://github.com/ActivityWatch/aw-android.git      # in aw-android
> git remote add ActivityWatch https://github.com/ActivityWatch/aw-server-rust.git  # in the submodule
> ```

**Drift as of 2026-09-02:** `aw-android` is **7 behind / 13 ahead**; `aw-server-rust` is
**1 behind / 4 ahead** (the one commit is an `aw-webui` bump — relevant to Phase 5, not Phase 1).
The collision surface is small and surgical: `SyncInterface.kt`, `SyncScheduler.kt`, `build.yml`,
`.gitmodules`, and `aw-sync/`. Docs collide with nothing.

> ⚠️ **`aw-android#249` is a trap, not a gift.** It fixes the same 401 as step 1.0, but its Kotlin
> half declares `private external fun setDataDir(path: String)`, requiring a JNI symbol this fork
> does not export → `UnsatisfiedLinkError` at load. Step 1.0 takes the fix by the `XDG_DATA_HOME`
> route instead, which needs no new symbol. **Skip `#249` when merging; do not cherry-pick it.**

**Cost:** budget **2–6 sessions across the project**. This tax scales with *calendar* time, not
with session count — an idle month still drifts a month — which is a real argument for working in
concentrated stretches rather than spreading them out.

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
| A fix for a bug we also have | **Read the diagnosis, port the fix by hand.** Their call sites may not exist here |

After any Rust merge: update the submodule pointer, push, rebuild in Actions
([`02`](02_ARCHITECTURE.md) §5).
---

## Progress log

*Newest first.*

### 2026-09-02 (later still) — Local type-checking set up; 1.2/1.3/1.6 now compile-verified
**R4's premise was partly wrong.** The machine already had JDK 21, the Android SDK with platform
36, MSVC 14.44, and **NDK `28.2.13676358` — the exact version `build.yml` pins**. Only Rust was
missing (~400 MB). Details and the full table in [`02`](02_ARCHITECTURE.md) §5.1;
`scripts/check-local.sh` runs both checks.

- **Kotlin** — `./gradlew :mobile:compileDebugKotlin` → BUILD SUCCESSFUL in 1m 12s. **1.1 and 1.6
  compile.** Needs no Rust: `cargoBuild` is not wired into the Gradle build.
- **Rust (host)** — `cargo check -p aw-sync --lib` → 24.75s. **1.2 and 1.3 compile.** It also found
  a stale `sync_datastores` import in `sync_wrapper.rs` (pre-existing), now removed;
  `sync_wrapper.rs` is warning-free. The 5 remaining warnings are host-only dead code in `util.rs`
  for functions `android.rs` and `main.rs` use.

⚠️ **`android.rs` is still unchecked, so steps 1.0 and 1.1's JNI half remain unverified** — it is
`#[cfg(target_os = "android")]`, which the host check skips **silently**. Checking it needs the
android target, which needs a from-source OpenSSL build, which does not work on Windows: OpenSSL's
`Configure` rejects Strawberry perl for emitting Windows paths, while Git Bash's perl emits Unix
paths but is missing core modules. Supplying the missing modules got `Configure` to pass fully;
`make` then failed on `$(CROSS_COMPILE)$(CC)` joining without a separator (`binclang.exe`).
Stopped there on purpose — `mobile/build.gradle` has upstream's own note, *"chokes on building
openssl-sys"*, and CI must run before device testing anyway.

**Net effect on the estimate:** the compile-error class of CI round-trip is now mostly gone for
Kotlin and for non-JNI Rust. JNI errors still cost a full CI cycle.

### 2026-09-02 (later) — Blocker 5 found; Blocker 3 corrected; 1.0–1.3 and 1.6 written
Prompted by the question *"does the estimate account for upstream maintenance?"* — it did not, and
checking produced two findings that mattered more than the number.

**Configured the `ActivityWatch` remote in both repos.** It did not exist, so this document's own
maintenance procedure had never been runnable. Drift: `aw-android` 7 behind, `aw-server-rust` 1.

**Blocker 5 — the JNI client never sent the API key.** `android.rs::get_client()` used plain
`AwClient::new()`, so every sync 401'd on `GET /api/0/buckets`, reported success, and moved
nothing. This is **upstream of Blockers 1–4**: push never obtains bucket data, so no `.db` appears
whatever the directory layout is. It is a regression this fork introduced — the multi-device
rewrite of `android.rs` dropped the call sites while keeping the supporting helpers. Upstream hit
the identical bug (`aw-android#247`) and fixed it in `#666`/`#249`. Fix ported by hand, not
cherry-picked: `#249`'s Kotlin needs a `setDataDir` JNI symbol this fork does not export, which
would have produced `UnsatisfiedLinkError` — the same failure as 2026-08-31.

**Blocker 3 was misdiagnosed.** `getDeviceId()` is genuinely non-unique, but its value never named
the device directory: `setup_local_remote` uses the *server's* `info.device_id`, and
`aw-server/src/device_id.rs` already persists a `Uuid::new_v4()`. D8 was already satisfied one
layer down. The Kotlin value's only consumer was the `_staging` directory that step 1.2 deletes.
Rather than mint a competing second identity, Kotlin now reads the server's over JNI (**D18**).

**Written, unbuilt:** 1.0, 1.1, 1.2, 1.3, 1.6. ⚠️ **None has been compiled or run.** No local
toolchain exists by design (R4), so CI is the first check any of them get — that is the next action.

Also worth recording: Blocker 4's `max_by_key` was only on the legacy `pull()` path; Android's
multi-device path already iterated every db. Fixed anyway, but it explains less of the original
symptom than the write-up implied.

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
