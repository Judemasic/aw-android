# 06 — Roadmap

> **👉 START HERE:** **Run a CI build, then 1.5** — two devices, real hardware.
> Phase 1 is now written end to end. 1.0a, 1.0b, 1.1, 1.2 and 1.6 are verified on device; **1.3,
> 1.4 and 1.7 are written and Kotlin-compile-checked but have never run** — a local build is not
> possible (R4), so CI is the first real check they get. 1.4 and 1.7 are Kotlin-only, so no
> submodule push is needed for them; if the submodule *has* changed, push `aw-server-rust@beta`
> **first** ([`02`](02_ARCHITECTURE.md) §5, cache trap), then the pointer, then build.
>
> Data now moves in both directions **in code**. Nothing proves it until 1.5 is green. Do not start
> the combined timeline first — it has no multi-device data to operate on and no way to prove it is
> right.

**How to work this document:** do one step, run its check, stop. Then update the step in place —
mark it `✅ DONE (date)`, write a **Result** saying what is *actually true now*, and flag with ⚠️
anything not verified. Append to the Progress Log at the bottom, newest first.

---

## Phase 1 — Make sync work *(blocking everything)*

Fixes the blockers in [`03_SYNC.md` §2](03_SYNC.md). Nothing here is new functionality; it is what
has to be true before any feature exists.

> **Status 2026-09-02 (end of day):** everything in Phase 1 except 1.5 is now written. 1.0a, 1.0b,
> 1.1, 1.2 and 1.6 are **verified on device**; 1.3, 1.4 and 1.7 are **compile-checked only**.
> Next: a CI build, then 1.5.
>
> | Step | State | How checked |
> |---|---|---|
> | 1.0a | ✅ on device | scheduler starts, no `UnsatisfiedLinkError` |
> | 1.0b | ✅ on device | API key forwarded, no 401 |
> | 1.1 | ✅ on device | server-minted UUID names the device directory |
> | 1.2 | ✅ on device | `<hostname>/<device_id>/test.db`, no `_staging` |
> | 1.3 | ⚠️ compiles | `cargo check` host — needs two dbs present, so needs 1.4 |
> | 1.4 | ⚠️ compiles | `compileDebugKotlin` — never run |
> | 1.6 | ✅ on device | viewport honoured, pinch-zoom works |
> | 1.7 | ⚠️ compiles | `compileDebugKotlin` — never run |

### 1.0a — Export the logging init under its JNI name ✅ VERIFIED ON DEVICE 2026-09-02
Blocker 6, found on device and **underneath everything else** — the sync scheduler disabled itself
before any sync code could run, which is why Blockers 1–5 were never observable.

`android.rs` exported `aw_sync_init_logging` as a plain C symbol; `SyncInterface.kt` declares
`external fun awSyncInitLogging`, so the JVM looked for
`Java_net_activitywatch_android_SyncInterface_awSyncInitLogging` and threw. Renamed, with the JNI
signature `(JNIEnv, JClass, i32)`.

**Result:** the symbol now matches. Guarded by `scripts/check-local.sh jni`, which diffs Kotlin
`external fun` names against the Rust exports — it reproduces the failure in under a second.
⚠️ Not yet rebuilt or run. **Check:** no `aw-sync native library unavailable` in logcat, and
`SyncInterface` initialises.

### 1.0b — Forward the API key from the JNI client ✅ VERIFIED ON DEVICE 2026-09-02
Blocker 5, found while checking upstream drift and **upstream of all the others** — without it push
obtains no bucket data at all, so no `.db` can appear whatever the layout is.

In `aw-sync/src/android.rs::get_client()`: call `apply_android_data_dir_from_env()` (ported from
upstream, recovers filesDir from `XDG_DATA_HOME`; no new JNI symbol), then build the client with
`AwClient::new_with_api_key()` using `util::get_server_config()`.

**Result:** `get_client` now sets the android data dir and forwards `[auth].api_key`.
✅ **Verified on device 2026-09-02:** `using API key from config.toml for local client` and `android data dir from XDG_DATA_HOME: /data/user/0/.../files`. No 401; the sync moved 475 events. **Check:** no `401` from `GET /api/0/buckets` in logcat during a sync, and
`using API key from config.toml for local client` appears at info level.

> **Do not cherry-pick `aw-android#249`** to get this — see the warning in
> [`03_SYNC.md` §2.5](03_SYNC.md).

### 1.1 — Unique device identity ✅ VERIFIED ON DEVICE 2026-09-02
**Rewritten from the original step** — see the correction box in
[`03_SYNC.md` §2.3](03_SYNC.md). `aw-server` already mints a persisted `Uuid::new_v4()`, and it is
that id — not Kotlin's — that names the device directory. So rather than minting a second one:

- `aw-sync/src/android.rs` exports `SyncInterface.getDeviceId(port)`, returning
  `client.get_info().device_id`.
- `SyncInterface.kt` replaces the old installer-package/`Build.FINGERPRINT` hash with
  `resolveDeviceId()`, which calls that and caches the result. Null means *not known yet*, never
  *mint a new one*.

**Result:** one identity across Kotlin and Rust, matching the `.db` path already on disk.
✅ **Verified on device 2026-09-02:** the tablet reported `7b54cfe9-ec39-4ec3-934c-67c81111d8e7`, a server-minted UUID v4, and it named the device directory in the shared folder. **Check:** two devices log two different UUIDs, and each matches its own
directory name under `<sync>/<hostname>/`. *(R22)*

> **Still owed:** the restore guard from [`05`](05_DATA_MODEL.md) §7. It now applies to
> aw-server's `device_id` file, which an app backup clones just as readily. Do it in Phase 2 with
> `meta.json`, which is what the guard compares against.

### 1.2 — Fix the push/pull depth mismatch ✅ VERIFIED ON DEVICE 2026-09-02
Dropped the `_staging` level in `push_with_hostname_and_device_id`; it now pushes to
`<sync>/<hostname>/` and lets `setup_local_remote` create `<device_id>/`. The `device_id` argument
is now log-only (documented in place).

**Result:** push and `get_remotes()` agree on `./{host}/{device_id}/*.db`.
✅ **Verified on device 2026-09-02:** `Creating new database file: .../sync/jude_s_tab_s10_fe/7b54cfe9-.../test.db` — exactly `<hostname>/<device_id>/test.db`, no `_staging`. **Check:** after a sync, `<sync>/<host>/<uuid>/test.db` exists at exactly that
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

### 1.4 — Bidirectional SAF mirror ✅ DONE 2026-09-02 ⚠️ *unverified*
The import pass exists. `SyncInterface.importPeerFilesFromSafDir()` walks the SAF tree and copies
`<hostname>/<device_id>/` into the app-private `syncDir` for **every device id except our own**,
reproducing the layout verbatim. There is deliberately **no separate staging directory**: the
engine only ever scans `AW_SYNC_DIR`, so a copy anywhere else would be invisible to it, and
`pull_all_from_all_hostnames` already walks exactly this tree. It runs at the top of the
multi-device cycle, before the pull.

Each file is copied to a `.aw-import-tmp` name and renamed into place, so the pull that follows can
never open a half-written database (**R24**); an interrupted copy is parked under a name the Rust
side's `.db` extension filter ignores. Unchanged files are skipped on size + mtime, with an unknown
timestamp counted as *changed* — a wrong skip loses peer data silently, which is the failure this
step exists to remove.

**Export is now restricted to our own `<hostname>/<device_id>/`**, and that is required rather than
tidy: with peers' databases living in `syncDir`, the old whole-tree mirror would have written every
peer's file back out under our own hand — which is precisely how Syncthing is made to produce
`.sync-conflict-*` copies (**R20**). Every hostname directory is scanned, not just the current one,
because a device rename leaves our id under the old hostname.

Both directions key off `resolveDeviceId()` from 1.1; if it returns null, both passes skip rather
than guess. SAF entry names are treated as untrusted input — dot-entries (`.stfolder`,
`.stversions`), anything containing a path separator, and `*.sync-conflict-*` are ignored.

**Result:** the mirror is bidirectional and Blocker 1 is closed in code.
✅ Compiles (`compileDebugKotlin`). Not run. **Check:** peer `.db` files appear in app-private
storage after a sync — `SAF import: peers=1 copied=1 …` in logcat, and
`<syncDir>/<their hostname>/<their uuid>/test.db` present on this device. *(R21, R24)*

### 1.5 — Two-device end-to-end verification ⬜
Run the full procedure in [`03_SYNC.md` §5](03_SYNC.md).
**Check:** device A displays a bucket only device B could have produced.

> **Phase 1 is not done until 1.5 passes on real hardware.** Everything downstream assumes
> cross-device data actually arrives; a green build proves nothing here. Five blockers have now
> been found by reading source, and the fifth was found only because upstream had already hit it —
> so do not treat the list as closed until 1.5 is green.

### 1.6 — WebView viewport quick fix ✅ DONE 2026-09-02 — ✅ VERIFIED ON DEVICE
Added to `WebUIFragment.onCreateView`: `useWideViewPort`, `loadWithOverviewMode`, `setSupportZoom`,
`builtInZoomControls`, and `displayZoomControls = false`.

Rationale in [`02_ARCHITECTURE.md` §7.1](02_ARCHITECTURE.md) — `useWideViewPort` defaults to
`false`, so `aw-webui`'s perfectly good mobile viewport tag was being discarded. *(R32)*

**Result:** the WebView now honours the page's viewport and permits pinch-zoom.
✅ **Verified on the phone (owner, 2026-09-02):** zoom works and cut-off content can be reached by panning. **R30 and R32 are satisfied** — content is no longer unreachable, which was the correctness issue. ⚠️ **R31 (no horizontal page scroll) is not yet confirmed** — see Q8. **Check:** the dashboard fits the screen width on load, and anything still
oversized can at least be reached by pinch-zooming. **Record what remains** — that list is the
input to Q8 and decides how large Phase 5 is.

### 1.7 — Sync settings: reachability and a manual trigger ✅ DONE 2026-09-02 ⚠️ *unverified*
Rode along on 1.4's build, as planned.

- **"Sync now"** in `SyncSettingsActivity` runs one `syncBothMultiDeviceAsync` and writes the
  result into a status line under the button. The `SyncInterface` is constructed on
  `Dispatchers.IO`, as `SyncScheduler` does — the constructor loads the `.so` and calls into JNI,
  which can block on a cold start. It deliberately does **not** require the sync switch to be on
  (an explicit tap is explicit intent, and it makes one sync testable without arming the 15-minute
  scheduler), but it does require a configured directory, since without one a sync reports success
  while sharing nothing.
- **The toolbar is back, and that was the whole bug.** The cause was in the layout, not in
  navigation: `app_bar_main.xml` had its entire `AppBarLayout` **commented out**, so `MainActivity`
  had no action bar at all — no hamburger button, and `R.menu.main` (which has carried a
  `Sync Settings` item and a working handler all along) was being inflated into nothing. That left
  the drawer's edge swipe as the only route, and current Android gives that gesture to system back.
  Uncommented it, and added `setSupportActionBar` + an `ActionBarDrawerToggle` in `MainActivity`.

**Result:** two routes to Sync settings on a stock device — hamburger → drawer, or overflow →
Sync Settings — and a sync can be triggered on demand with its outcome visible in the app.
✅ Compiles (`compileDebugKotlin`). Not run. ⚠️ The toolbar takes ~56dp of height off the WebView;
re-check the 5.1 audit once this is on a device. **Check:** on a stock device with default gesture
navigation and no OS-level settings changed, reach Sync settings, tap **Sync Now**, and see a
result line appear.

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

### 5.1 — Audit what actually breaks ✅ MOSTLY DONE 2026-09-02
On a real phone in portrait, list every screen that overflows, with the offending element.

**Result (owner, on the phone, after 1.6):**

| Screen | State |
|---|---|
| Activity | ✅ fine |
| Timeline — **range and mode controls**, top bar | ❌ overflows |
| Settings — **some fields** | ❌ overflow |

Tablet is fine throughout, so this is **narrow-width only**. ⚠️ Still owed: the *specific* offending
elements (which CSS rule, which component) — the list above is at screen granularity, which is
enough to decide Q8 but not enough to fix. Pin them down with WebView remote debugging
(`chrome://inspect`), which works because `WebView.setWebContentsDebuggingEnabled(true)` is already
set in testing builds.
### 5.2 — Decide Q8 ✅ RESOLVED 2026-09-02 — CSS, not native
Patch `aw-webui`'s CSS (**B**), or inject a mobile stylesheet from the WebView (**A**).

**Decided:** the phone audit (5.1) found only a few overflowing control groups while the tablet is
fine throughout, so **C (native screens) is ruled out** — it would rewrite screens that already work
everywhere except a handful of rows. Prefer **B** where the fix is a genuine responsive improvement
worth carrying upstream; fall back to **A** for anything too fork-specific to upstream.

### 5.3 — Kill horizontal page scroll ⬜
The page must not scroll sideways; wide tables and charts scroll inside their own containers
instead. *(R31)*
**Check:** no screen scrolls the page horizontally in portrait.

### 5.4 — Touch targets and navigation ⬜
Controls sized for a thumb, not a mouse.

> **Confirmed on device (owner, 2026-09-02):** the navigation drawer **cannot be opened normally on
> current Android** — edge-swipe is consumed by the system back gesture. The owner reached Sync
> settings only by changing an OS-level setting to restore edge swipe. Sync settings are therefore
> **effectively unreachable on a stock device**, which makes this a functional defect (**R30**),
> not a polish item.
>
> Worth pulling forward: Phase 1 needs Sync settings repeatedly, so every device iteration
> currently depends on an OS workaround the owner had to discover.
>
> ✅ **Addressed in 1.7 (2026-09-02, unverified):** the action bar was commented out of
> `app_bar_main.xml`, so there was no hamburger button and no overflow menu. Restoring it gives
> back both routes. Re-test this item on a device before considering it closed.

> **Also missing: a manual "Sync now" control.** `SyncSettingsActivity` offers only a directory
> picker and an on/off switch. Sync is time-driven — `SyncScheduler` runs the first sync ~1 minute
> after being enabled, then every 15 minutes — so there is no way to *make* a sync happen, and no
> feedback about whether one ran or what it did. Tolerable in normal use; a real drag on device
> verification (1.5), where every check means waiting out a timer and guessing.
>
> ✅ **Added in 1.7 (2026-09-02, unverified).**

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

### 2026-09-02 (end of day) — Blocker 1 closed in code: the mirror finally goes both ways
**1.4** and **1.7** written together, in one build, as 1.7 planned.

The import pass is the whole of 1.4: peers' `<hostname>/<device_id>/` directories are copied out of
the SAF folder into the app-private `syncDir` before every pull, and export is now narrowed to our
own directory. That narrowing is not tidiness — without it the very next export would have copied
every peer's database back out under our own hand, breaking the single-writer rule (R20) that keeps
Syncthing from producing conflict files. Files land via `.aw-import-tmp` + rename so the pull can
never open a partial database (R24).

1.7 turned out to be smaller than written down. The drawer was not fighting the back gesture in any
interesting way: `app_bar_main.xml` had its `AppBarLayout` **commented out**, so `MainActivity` had
no action bar at all. `R.menu.main` has carried a `Sync Settings` item, and `onOptionsItemSelected`
a working handler for it, the entire time — inflated into nothing. Uncommenting the toolbar
restores the hamburger *and* the overflow route in one move. "Sync Now" sits under the directory
picker and reports its result inline.

Everything here is **Kotlin and XML only** — no submodule change, so the next CI build needs only
the app. `compileDebugKotlin` passes (resources included, which is what proves the restored layout
and the new view ids resolve). ⚠️ **Nothing has run.** 1.4's import path, the restored toolbar and
the Sync Now button have never executed on a device, and **1.3 is still unproven** — it needs two
databases present, which is exactly what 1.4 is supposed to produce. That is 1.5.

### 2026-09-02 (late) — Sync runs for the first time; five of six blockers verified on hardware
The Blocker 6 fix landed and `SyncInterface` constructed successfully for the first time. The very
next scheduled sync produced, in one run:

```
android data dir from XDG_DATA_HOME: /data/user/0/net.activitywatch.android.debug/files
using API key from config.toml for local client
Creating new database file: .../sync/jude_s_tab_s10_fe/7b54cfe9-.../test.db
= Synced 431 new events        (aw-watcher-android)
= Synced 44 new events         (aw-watcher-android-unlock)
Multi-Device Sync completed: success=true
SAF mirror: copied=1 skipped=0 → content://.../primary%3AActivityWatch-sync
```

**The original symptom is gone.** `/storage/emulated/0/ActivityWatch-sync/jude_s_tab_s10_fe/
7b54cfe9-ec39-4ec3-934c-67c81111d8e7/test.db`, 114,688 bytes, 475 real events.

Verified on device: **1.0a** (scheduler starts, no `UnsatisfiedLinkError`), **1.0b** (API key
forwarded, data dir resolved — no 401), **1.1** (`7b54cfe9-…`, a server-minted UUID v4, naming the
device directory exactly as **D18** predicted), **1.2** (`<hostname>/<device_id>/test.db`, no
`_staging`). **1.6** was verified earlier on the phone.

Every fix that was reasoned from source turned out to be correct. What made them invisible for two
days was Blocker 6 — a JNI symbol name — sitting underneath all of them.

⚠️ **Not yet proven: 1.3.** Pulling every database needs more than one database present, which needs
1.4. ⚠️ **Blocker 1 remains and is now the only architectural gap:** the SAF mirror is
**export-only**, so a device still cannot read a peer's data. That is **1.4**, and it is the one
thing standing between here and real multi-device sync.

Also this session: debug signing fixed (the in-place upgrade preserved `syncEnabled` and the SAF
`syncDirUri`, proving it), and the `Co-Authored-By` trailers were stripped from `beta` in both
repos at the owner's request — `master` and the upstream commit `928814f` deliberately untouched.

### 2026-09-02 (night) — Blocker 6 found on device: sync never ran at all
The tablet's logcat, on the first install of a working build:

```
E/SyncScheduler: aw-sync native library unavailable; sync scheduler disabled
E/SyncScheduler: java.lang.UnsatisfiedLinkError: No implementation found for void
  net.activitywatch.android.SyncInterface.awSyncInitLogging(int)
  at net.activitywatch.android.SyncInterface.<init>(SyncInterface.kt:63)
```

`android.rs` exported the logging init as a plain C symbol, `aw_sync_init_logging`, while Kotlin
declares `external fun awSyncInitLogging` — so the JVM looked for
`Java_net_activitywatch_android_SyncInterface_awSyncInitLogging` and found nothing. Every *other*
export in the file was correctly named; this one was the exception, introduced with the
`catch_unwind` work on 2026-09-01.

**This sat underneath Blockers 1–5 the whole time.** `SyncInterface`'s constructor threw, so the
object could never be built and no JNI function was reachable. All the reasoning about directory
depth, API keys and device ids was correct *and completely unobservable*, because none of that code
had ever executed. It also means the 2026-09-01 entry's "real fix" for JNI panics shipped a symbol
the JVM could not resolve.

**The methodological point, stated plainly:** five blockers were found by reading source and every
one was real, but the thing actually stopping sync was a name mismatch that source-reading cannot
surface. The Rust compiles, the `.so` loads, and the symbol is merely absent under the name Java
asks for. Not a syntax error, and `android.rs` is `cfg`-gated away from the host check. **D20 said
the blocker list stays open until 1.5 is green; this is the second confirmation.**

Now guarded by `scripts/check-local.sh jni`, diffing every Kotlin `external fun` against the Rust
exports. It caught the bug immediately on its first run — against the submodule, which had not yet
received the fix. `RustInterface`/`aw-server` was cross-checked at the same time and is clean.

Also this session: 1.6 **verified on the phone** and Q8 effectively resolved (CSS, not native); the
debug-signing fix landed so device installs no longer wipe history.

### 2026-09-02 (evening) — First green build; debug signing fixed
Build [33665149124](https://github.com/Judemasic/aw-android/actions/runs/33665149124) is **green** —
all jobs, including `Test` and `Test E2E`. The first attempt failed on a stray `}` in `android.rs`
from an unbalanced edit, in exactly the file local checks cannot reach; now guarded by a rustfmt
parse pass (`scripts/check-local.sh syntax`), which reproduces CI's message in under a second.

**Installing it revealed a bigger problem than the build.** `INSTALL_FAILED_UPDATE_INCOMPATIBLE` —
`mobile/build.gradle`'s `debug` block had no `signingConfig`, so every ephemeral CI runner signed
with its own generated key. Every device test therefore required an uninstall, wiping collected
history. Fixed with a committed debug keystore ([`02`](02_ARCHITECTURE.md) §5.2, **D23**). The
failed install was **non-destructive** — nothing was removed.

**Two design assumptions confirmed on real hardware**, read out of the tablet's app-private storage
via `run-as`:
- `files/device_id` = `f64bb5bb-…` — a genuine `Uuid::new_v4()`, exactly as **D18** assumed. The
  server-owned identity is real, and minting a second one in Kotlin would have been wrong.
- `files/config.toml` carries `[auth].api_key`. **Blocker 5 is confirmed live**: an API key is set,
  and the old `get_client` sent none, so it was certainly 401ing. Diagnosed from source, now proven
  from the device.

Tablet data backed up to `C:/dev/aw-backups/` (774 KB) before any of this. ⚠️ Note the WAL held
**725 KB against a 4 KB `sqlite.db`** — a backup that copies only `sqlite.db` loses almost everything.

⚠️ Still nothing verified *running*: the new APK is not yet installed on any device.

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
