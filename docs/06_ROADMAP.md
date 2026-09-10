# 06 — Roadmap

> **👉 START HERE:** ✅ **Phase 1 and Phase 2 are done and verified on both devices as of
> 2026-09-09**, through [2.3a](#23a--apply-settings-while-the-app-is-open). **Phase 3 is under way:
> [3.1](#31--origin-tagging-at-merge) is verified on both devices (2026-09-09, CI [34343835497] /
> `feb127e`), and [3.2](#32--segmentation--classification) and
> [3.3](#33--provisional-attribution) are done in code (2026-09-09)** — the `aw-combined` crate in
> `aw-server-rust` now has the full normalise/segment/classify/**attribute** pipeline plus a
> separate opt-in `coalesce`, with 42 passing tests including a direct R6 invariant suite. Nothing
> calls it yet, so there is nothing to verify on a device. The submodule pointer moved to
> `aw-server-rust@beta` `a39f52e`, which carries only the (still unused) crate — no CI build or APK
> needed.
>
> ✅ **[3.4](#34--combined-view-with-shading) is verified on both devices (2026-09-09**, CI
> [34366963972] / `9f6f855`). The crate is wired to something at last: a **Combined timeline**
> screen in the nav drawer, the combined track above the per-device tracks with unresolved
> contention shaded (**R8**). **R6 is visibly holding** — the phone reads *"7h 50m combined, from
> 9h 29m across 2 device(s)"*. Two device-only bugs were found and fixed on hardware (a
> ThreeTenABP init-order crash, and a device contending with *itself* because origin was resolved
> per event rather than per bucket); both are written up in 3.4. `aw-server-rust@beta` is
> `9f6f855`.
>
> ⏳ **[3.5](#35--rebuild-the-combined-timeline-in-aw-webui) is the live step, and it moves the
> combined timeline out of Kotlin and into aw-webui.** The owner raised the bar on 2026-09-09 —
> *"top of the line ui not just a few adjustments"*, look like the **Activity view**, and **work on
> PC, tablet and phone** (now **R35**/**R36**). That last one is decisive rather than cosmetic: 3.4
> drew the screen with a native Android `View`, and there is no Android on a PC, so no amount of
> restyling can get there. **3.5a is done** — the pipeline is now served at
> `GET /api/0/combined/timeline`, verified against a running server (R6 and R8 both hold over
> HTTP). **3.5b is verified on device** (the owner confirmed pinch-zoom works), and **3.5c has taken
> the native screen out** — there is now exactly one combined view, the Vue one, and the drawer's
> "Combined timeline" entry opens it. **4.1, the resolution sheet, is built** and verified in a
> browser at phone and desktop width; **4.2, persisting decisions, is next.**
>
> ⚠️ **Do not treat the current native screen as the intended one** — its defect list, in 3.4, is
> now 3.5b's acceptance list. Separately,
> [5.5](#55--make-the-aw-webui-timeline-usable-at-phone-width) tracks the owner's request that
> upstream aw-webui's **Timeline** be made usable at phone width; different screen, same repo.
>
> **Decided, not built: [2.3b](#23b--show-when-settings-last-synced).** No second "Sync Now"
> button — [1.7](#17--sync-settings-reachability-and-a-manual-trigger) already put one in Sync
> settings and its cycle ends with the settings step. What is missing is *visibility*, so 2.3b is
> a line of text, not a button. It can be done any time; it does not block 3.3.
>
> **The 4.2% ceiling is gone, measured not assumed (2026-09-04, step
> [1.11](#111--bump-aw-webui-past-the-960-fix)).** `aw-webui` moved `3cbe349 → a2ca625`, carried
> through `Judemasic/aw-server-rust@beta` (`9e01fab`) then `Judemasic/aw-android@beta` (`1524318`)
> in that order because the submodule moved. Both devices run the resulting build, and on the
> tablet the phone's synced history goes **48,186s → 399,383s (8.3×)** under the fixed merge keys —
> **no re-sync, no migration.** The data had been there the whole time.
>
> **Phase 1 has no code left in it.** 1.9's failure path — the last untested thing — ran on
> **2026-09-08**: `Sync failed:` reaches the status line naming *both* the import and the export,
> at warning level. It was drivable from adb after all, via a stale `syncDirUri`; read
> [1.9](#19--a-sync-that-failed-must-not-report-success) for what that does and does not prove, and
> for **why the Syncthing-managed sync folder must never be renamed to force a failure** (D25/D26).
>
> **The only thing still owed is a decision, not code: [1.10](#110--timeline-truncates-every-peers-name-at-the-first-_)** (below).
>
> **Where the repos stand (2026-09-08), both pushed:**
>
> | Repo | Branch | At | Note |
> |---|---|---|---|
> | `Judemasic/aw-server-rust` | `beta` | `b462665` | upstream `master` merged in — category-rule priority ([#663]) and query changes. `cargo check` + 76 tests pass locally |
> | `Judemasic/aw-android` | `beta` | see the 2026-09-09 log | 2.1 landed (`SharedFolder.kt` + docs); the server pin is untouched |
>
> ⚠️ **`aw-android` still pins `aw-server-rust` at `9e01fab`, deliberately — one commit behind that
> merge.** The merge brought in **zero** Android-relevant code (four files, all `aw-query` /
> `aw-transform`; no `android`, `jni` or `aw-sync` hits), so bumping the pin would put unverified
> server code into the next Android build and invalidate 1.11's hardware verification for no gain.
> Bump it when something on the server side is actually needed, then re-verify on device — the same
> way [1.11](#111--bump-aw-webui-past-the-960-fix) was done.
>
> Also newly in the build and unexamined: [#966] (`prompt()` → modals, so WebView dialogs stop being
> silent no-ops) and [#956] (category JSON import over SAF). Worth a glance next time a device is in
> hand.
>
> **Waiting on the owner, not on code: [1.10](#110--timeline-truncates-every-peers-name-at-the-first-_).**
> The Timeline labels every peer `android-synced-from-jude` because aw-webui cuts the hostname at
> its first underscore. **You do not have to rename to work around this** — the Timeline's
> `Filters ▸ Host:` dropdown reads the untruncated `hostname` metadata and already distinguishes
> the devices. Renaming to underscore-free names (`S25U`, `Tab-S10FE`) fixes the *label* too, but
> costs: the old sync directory is stranded as a phantom peer, **and already-synced buckets keep
> their old ids**, so history splits across `…-synced-from-jude_s_s25_ultra` and
> `…-synced-from-s25u`. Read 1.10 before deciding. `a2ca625` does **not** fix it — the bug is still
> live on upstream master.
>
> **Phase 2 starts at [2.1](#21--shared-folder-layout--version)**, and note the debt 1.1 left it:
> the restore guard from [`05`](05_DATA_MODEL.md) §7 belongs with `meta.json`, because an app backup
> clones aw-server's `device_id` file just as readily.
>
> The shared folder was checked on 2026-09-03 and is **clean** — one directory per device. Two
> stale databases remain in app-private storage; only one is a real leftover, and 1.5 says which.
>
> ⚠️ **The submodule has now moved once, so the habit matters:** whenever `aw-server-rust` changes,
> push `aw-server-rust@beta` **first**, then the pointer in `aw-android`, then build.

[#251]: https://github.com/ActivityWatch/aw-android/pull/251
[aw-webui#959]: https://github.com/ActivityWatch/aw-webui/issues/959
[#663]: https://github.com/ActivityWatch/aw-server-rust/pull/663
[#966]: https://github.com/ActivityWatch/aw-webui/pull/966
[#956]: https://github.com/ActivityWatch/aw-webui/pull/956
[34343835497]: https://github.com/Judemasic/aw-android/actions/runs/34343835497

**How to work this document:** do one step, run its check, stop. Then update the step in place —
mark it `✅ DONE (date)`, write a **Result** saying what is *actually true now*, and flag with ⚠️
anything not verified. Append to the Progress Log at the bottom, newest first.

---

## Phase 1 — Make sync work *(blocking everything)*

Fixes the blockers in [`03_SYNC.md` §2](03_SYNC.md). Nothing here is new functionality; it is what
has to be true before any feature exists.

> **Status 2026-09-04:** ✅ **Phase 1 is complete.** Cross-device sync works end to end on hardware,
> the dashboard shows the result, and **1.8's 4.2% ceiling is gone** — 1.11 bumped `aw-webui` past
> the upstream fix and the tablet now resolves the phone's synced history at **399,383s** against
> **48,186s** before, with no re-sync. Steps 1.0a–1.11 are verified on device.
>
> **No holes remain in 1.9:** the *failure* path ran on 2026-09-08 and reports `Sync failed:` at
> warning level. It turned out to be adb-drivable via a stale `syncDirUri`; 1.10 is an owner
> decision, not code.
>
> | Step | State | How checked |
> |---|---|---|
> | 1.0a | ✅ on device | scheduler starts, no `UnsatisfiedLinkError` |
> | 1.0b | ✅ on device | API key forwarded, no 401 |
> | 1.1 | ✅ on device | server-minted UUID names the device directory |
> | 1.2 | ✅ on device | `<hostname>/<device_id>/test.db`, no `_staging` |
> | 1.3 | ✅ on device | four dbs present, five `Synced` lines, no `choosing largest db` |
> | 1.4 | ✅ on device | `peers=2 copied=2`, ~7,000 events pulled from the phone |
> | 1.4a | ✅ on device | both devices log `SAF export: <hostname>/<own uuid>` — scoped |
> | 1.5 | ✅ on device | tablet holds 6,797 phone events and the Activity query returns non-zero |
> | 1.6 | ✅ on device | viewport honoured, pinch-zoom works |
> | 1.7 | ✅ on device | owner reached Sync settings and tapped **Sync Now** on the tablet |
> | 1.8 | ✅ on device | Activity **0.0s → 317.5s**; **aw-webui#959 fixed upstream** by #960 |
> | 1.9 | ✅ on device | healthy: `failed=0`, export precedes callback. failure: `Sync failed:` at **warn** level, 2026-09-08 |
> | 1.10 | 🔎 found | Timeline truncates peer names at the first `_` — upstream, still unfixed on master |
> | 1.11 | ✅ on device | tablet: the phone's synced history **48,186s → 399,383s** (8.3×), no re-sync |

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

### 1.3 — Pull every database, never the largest ✅ VERIFIED ON DEVICE 2026-09-02
Replaced `max_by_key(len)` in `sync_wrapper.rs::pull()` with iteration over all discovered dbs, and
deleted the unused `device_id` local in `pull_from_hostname`.

**Result:** no path selects a single db by size.
✅ Compiles (`cargo check` host); `sync_wrapper.rs` is now warning-free. Not run. **Note:** this was **half-satisfied already** — Android's multi-device path is
`pull_all_from_all_hostnames` → `pull_from_hostname`, which already iterated every db. The
`max_by_key` was only on the legacy `pull()` path reached via `syncPullAll`. Blocker 4 was
therefore real but not on the live path, which lowers its share of the original symptom.
✅ **Verified on device 2026-09-02:** with four databases present the pull produced five separate
`= Synced N new events` lines and no `choosing largest db`. Still owed: the same check with three
*distinct* devices rather than two. **Check:** no `"choosing largest db"` in logs with three dbs
present. *(R19)*

### 1.4 — Bidirectional SAF mirror ✅ VERIFIED ON DEVICE 2026-09-02
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

**Result:** Blocker 1 is closed, and **cross-device sync works**. First run with the phone's data
present in the shared folder, triggered from the new Sync Now button:

```
SAF import: peers=2 copied=2 skipped=0
Creating new database file: …/sync/jude_s_s25_ultra/7b54cfe9-…/test.db
= Synced 728 new events
= Synced 6280 new events
= Synced 14 / 1 / 20 new events
Multi-Device Sync completed: success=true
```

✅ **The tablet pulled ~7,000 events off the phone.** That is R21 satisfied on hardware, and it is
also the first time **1.3** ran with more than one database present.

> ⚠️ **`peers=2` was one phone reached by two paths, and that exposed a bug in the export** — see
> the fixed-in-place note below. The count is expected to read `peers=1` on the next build.

**Check:** peer `.db` files appear in app-private storage after a sync — `SAF import: peers=1 …`
in logcat, and `<syncDir>/<their hostname>/<their uuid>/test.db` present on this device.
*(R21, R24)*

#### 1.4a — Export scope narrowed to the current hostname ✅ FIXED 2026-09-02 ⚠️ *unverified*
Found the moment two devices met. `sync_run` in **pull** mode calls
`setup_local_remote(<peer hostname>, our_device_id)`, which creates
`<peer hostname>/<our device id>/test.db` in app-private storage as a side effect of reading a
peer. The export scanned **every** hostname directory for our device id — a deliberate choice, to
survive a device rename — and so published our own database into *the peer's* hostname folder as
well. The phone's older export-everything build did the mirror image. Two devices produced four
directories:

```
ActivityWatch-sync/jude_s_s25_ultra/ad0c6c34-…/    ← phone's, correct
ActivityWatch-sync/jude_s_s25_ultra/7b54cfe9-…/    ← tablet's db under the PHONE's hostname
ActivityWatch-sync/jude_s_tab_s10_fe/7b54cfe9-…/   ← tablet's, correct
ActivityWatch-sync/jude_s_tab_s10_fe/ad0c6c34-…/   ← phone's db under the TABLET's hostname
```

**Not an R20 violation** — every file is still written by exactly one device, the id in its path,
so Syncthing produces no conflict copies. But it grows as the square of the device count, inflates
the `peers=` count, and makes every device pull the same data twice.

Export is now limited to `<current hostname>/<our device id>/`. The rename case does not justify
the wider scan: after a rename the old directory is a stale snapshot either way, and the current
hostname gets a full push on the next sync.

⚠️ **Owed:** the four stale directories above are still in the shared folder. Once both devices run
a build with this fix, delete the two wrong ones by hand — nothing deletes them automatically, and
each will keep being imported as a phantom peer until it goes.

### 1.5 — Two-device end-to-end verification ✅ VERIFIED ON DEVICE 2026-09-03
Run the full procedure in [`03_SYNC.md` §5](03_SYNC.md).

✅ **Steps 1–3 pass.** Two devices, two distinct server-minted UUIDs
(`7b54cfe9-…` tablet, `ad0c6c34-…` phone), both directories present in the shared folder, and the
tablet's datastore took ~7,000 events that only the phone could have produced.

✅ **Both directions move files.** The phone is on a current build and imports too — steady state
on 2026-09-02 was `SAF import: peers=1 copied=1` and `SAF export: <own hostname>/<own uuid>
copied=1` on *each* device, which is 1.4 and 1.4a working symmetrically.

✅ **Step 4 — *displays* — passes, 2026-09-03, tablet attached over adb.** The tablet's own server
lists a bucket it could not have produced, carrying the phone's hostname:

```
aw-watcher-android-synced-from-jude_s_s25_ultra | currentwindow | jude_s_s25_ultra   6797 events
aw-watcher-android                              | currentwindow | jude_s_tab_s10_fe   485 events
```

The peer's buckets arrive under `<bucket>-synced-from-<hostname>` with `hostname` set to the
**origin** device, which is what makes them selectable as a separate host in the dashboard. And the
query behind the Activity view returns non-zero for that host — measured against the phone's bucket
on the tablet, over 2026-08-01 → 09-04:

| Query | Result |
|---|---|
| `flood(query_bucket(…))` | **366,699.9s** |
| … `+ merge_events_by_keys(events, ["app", "title"])` | **15,503.1s** |
| … `+ merge_events_by_keys(events, ["app"])` | **366,699.9s** |

**That is the check satisfied: 15,503.1s — 4h18m of the phone's activity, visible on the tablet.**
Two bugs stood in front of this and both are fixed: `MainActivity` opened `/#/activity/unknown/`, a
sentinel hostname with no buckets behind it (upstream **#250**, merged), and the Activity query
dropped every title-less event (**1.8**).

> ⚠️ **The middle row is the real story, and it is 1.8's caveat measured across sync: 15,503 of
> 366,700 seconds — 4.2%.** The other 96% is phone history recorded *before* the title fix, and it
> is still dropped by the upstream merge. Sync is not the limiter; aw-webui#959 is. A fix upstream
> would make all of it appear at once, with no re-sync — the events are already on the tablet.
> Nothing in this fork can recover them, because the missing `title` was never recorded.

> ✅ **The dot-prefix guard is earning its place.** Syncthing file versioning is on, so the shared
> folder also contains **`.stversions/`** — and inside it, archived copies of the stale directories
> 1.4a created, laid out as `.stversions/<hostname>/<uuid>/test.db`. That is a perfect decoy: it
> has the exact shape the importer looks for. `isSafeEntryName()` rejects any entry starting with
> `.`, and the logs prove it works — syncs report `peers=1`, not `peers=2`, with those archives
> sitting right there. Without that guard every deleted peer would resurrect itself forever.

> ⚠️ **Two stale databases remain in *app-private* storage** (the shared folder is clean). Only one
> is a leftover; the other is by design, and telling them apart matters:
>
> | Path | Size | Last written | What it is |
> |---|---|---|---|
> | `jude_s_tab_s10_fe/ad0c6c34-…` | 45 KB | 09-02 22:17 | **true leftover** — delete; nothing recreates it |
> | `jude_s_s25_ultra/7b54cfe9-…` | 45 KB | 09-02 21:56 | **regenerates** — `sync_run`'s pull calls `setup_local_remote(<peer host>, our_id)`; deleting it is pointless |
>
> Compare the live pair: `jude_s_tab_s10_fe/7b54cfe9-…` (124 KB, ours) and
> `jude_s_s25_ultra/ad0c6c34-…` (1.4 MB, the phone's), both written within the last ten minutes.
> Cost of the leftovers is a redundant pull every 15 minutes, not wrong data — events dedupe by id.

> ℹ️ **Noted, not chased:** `aw-stopwatch-synced-from-ad0c6c34-…` names the peer by **device
> UUID** where every watcher bucket names it by **hostname**. Upstream inconsistency in the
> stopwatch path, harmless here — it makes that one bucket sort oddly in a host list. Revisit if
> Phase 2 leans on bucket naming.

**Check:** device A displays a bucket only device B could have produced. ✅

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

### 1.7 — Sync settings: reachability and a manual trigger ✅ VERIFIED ON DEVICE 2026-09-02
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
✅ **Verified on the tablet by the owner 2026-09-02:** Sync settings were reached and **Sync Now**
was tapped, producing `Manual sync finished: success=true` — and that manual run is the one that
proved 1.4. The button paid for itself on its first use: the alternative was waiting out a
15-minute timer.
⚠️ Still owed: the same walk on the **phone**, which is the device where the drawer was actually
unreachable, and where the toolbar's ~56dp will matter to the 5.1 audit.

### 1.8 — Emit a `title` so the Activity view works ✅ VERIFIED ON DEVICE 2026-09-02
Not a sync step, but it sits in Phase 1 because until it is true the app shows the owner nothing,
and 1.5's "device A *displays* B's data" check cannot pass.

`aw-watcher-android` events carry `{app, package, classname}`. aw-webui's Android query runs
`merge_events_by_keys(events, ["app", "title"])`, and that helper drops every event missing any
requested key — so a full day evaluates to zero and the Activity view reads **"Time active: 0s"**
next to a Timeline full of events.

Measured on the tablet, same bucket, same day:

| Query | Result |
|---|---|
| `flood(query_bucket("aw-watcher-android"))` | **4878.8s** |
| … `+ merge_events_by_keys(events, ["app", "title"])` | **0.0s** |
| … `+ merge_events_by_keys(events, ["app"])` | **4878.8s** |

**Upstream regression:** aw-webui `bf0fc84` (2026-07-24), an iOS ScreenTime patch that changed the
shared Android branch from `["app"]` to `["app", "title"]`.

> ⚠️ **A blame-scoping claim was made here and was wrong.** An earlier draft said the regression
> shipped in **v0.14.0b2** and had "nine days of exposure". It does not ship there. Verified by
> ancestry, not by comparing dates:
>
> | Build | → aw-server-rust | → aw-webui | Contains `bf0fc84`? |
> |---|---|---|---|
> | **v0.14.0b2** | `e8e6e90` | `749585f` (2026-07-22) | **no** — `git merge-base --is-ancestor` says clean |
> | **this fork / master builds** | `c6f7df2` | `3cbe349` (2026-08-26) | **yes** |
>
> So **released Android users are unaffected**; only master/CI builds are. Reproduce with
> `git merge-base --is-ancestor bf0fc84 <pin>` inside `aw-server-rust/aw-webui` — dates alone are
> not proof, because a pin can be older than its own commit date suggests.

> ✅ **FIXED UPSTREAM 2026-09-03 — [aw-webui#960], merged as `85db7b5`.** Closed as completed the
> morning after the report. The maintainer credits it directly: *"Reported by Judemasic in
> ActivityWatch/aw-android#247, full analysis in #959."*
>
> The shipped fix is **source-aware merge keys**, not a blanket revert: an optional `isIos` flag on
> `AndroidQueryParams` lets `appQuery()` keep `["app", "classname", "title"]` for ScreenTime
> buckets while the `aw-watcher-android` path merges on `["app"]` / `["app", "classname"]`. A
> regression test covers both. (An earlier PR, #964, was closed as a duplicate — its unconditional
> `classname` key would have regressed ScreenTime.)
>
> **What this means for us — this is the important part:**
> 1. **The 4.2% ceiling lifts.** Android events no longer need `title`, so the ~96% of history
>    recorded *before* 1.8 becomes visible. It is already synced and sitting on both devices; no
>    re-sync is needed. **It requires bumping the `aw-webui` submodule past `85db7b5` — see the
>    START HERE block.**
> 2. **1.8 is now redundant, and should still stay.** With Android merging on `["app", "classname"]`
>    the `title` we emit is no longer a merge key, so it neither helps nor hurts that query. It is
>    kept because it costs one duplicated string per event, `title` is a field every other watcher
>    emits, and a real field cannot be re-broken by a future query edit — which is precisely how
>    this regression happened. Revisit only if upstream gives Android a *meaningful* title.

**Reported upstream 2026-09-02 as [aw-webui#959](https://github.com/ActivityWatch/aw-webui/issues/959)**
— filed against **aw-webui**, not aw-android, because the faulty query lives there and the same
`canonicalEvents` path serves every client. Root cause noted in that thread: aw-webui's "Android"
branch is shared with Apple ScreenTime imports (`bucketsAndroid()` returns android *and*
`aw-import-screentime` buckets), so PR #917 tightened the merge keys for iOS and silently broke
Android. #917's own body lists "events without `title` were silently skipped" as a bug it *fixed*
for iOS while introducing it for Android.

`title` is set to the app label in `Event.kt` and `SessionModels.kt`. Emitting the field beats
patching aw-webui: two forks instead of three, and it survives the query changing again. The
specific screen stays in `classname`, which `title_events` groups by.

⚠️ **Only new events get a title.** Everything recorded before this build stays invisible to that
query — the Activity view will fill in going forward, not retroactively.
✅ **Verified on the tablet 2026-09-02.** Same bucket, same day, before and after installing this
build: **Time active 0.0s → 317.5s**, with **Top Applications** populated. The owner confirmed it
on screen. The 317.5s is small only because it counts events recorded *after* the install, which is
exactly the caveat above. **Check:** Activity shows a non-zero **Time active** and a populated
**Top Applications** for a day recorded after this build.

### 1.10 — Timeline truncates every peer's name at the first `_` 🔎 FOUND 2026-09-03, NOT FIXED
Reported by the owner: *"in the timeline the names are different from the activity … I don't know
which for the S25U and which for the tab."* Not a naming preference — the hostname is being
**cut off**, and with three devices every peer would look identical.

aw-webui's `shortenBucketLabel` (`src/util/timelineLabels.ts`, added in `dc02ac8`, 2026-06-08)
drops everything from the **first underscore** onward, because on desktop the underscore separates
the host: `aw-watcher-window_erb-m2.localdomain` → `window`. Android's synced buckets do not have
that shape. They are `aw-watcher-android-synced-from-<hostname>`, and the hostname *itself*
contains the underscores — so the cut lands inside the peer's name.

There is a branch meant to catch exactly this, but it cannot fire:
`/^([^_]+)_.*-synced-from-(.+)$/` requires an underscore *before* `-synced-from-`, which is the
desktop layout. Android's base id `aw-watcher-android` has no underscore at all, so the match fails
and the naive shortener runs instead. Verified by simulating the function against this device's
real bucket list:

| Bucket | Timeline label |
|---|---|
| `aw-watcher-android` | `android` |
| `aw-watcher-android-synced-from-jude_s_s25_ultra` | **`android-synced-from-jude`** |
| `aw-watcher-android-media-synced-from-jude_s_s25_ultra` | **`android-media-synced-from-jude`** |
| `aw-watcher-android-web-synced-from-jude_s_s25_ultra` | **`android-web-synced-from-jude`** |

Every device owned by the same person collapses to `…-synced-from-jude`. The full id survives only
in the hover tooltip, which is unusable on a touchscreen.

**Why Activity looks different:** Activity selects by **hostname** (`jude_s_s25_ultra`), Timeline
labels by **bucket id**. Two vocabularies for one thing, and only one of them is truncated.

> **Workaround, no code required: rename the device so its name has no spaces.**
> `deviceHostname()` reads Android's `Settings.Global.DEVICE_NAME` and `sanitizeDeviceHostname`
> lowercases it and replaces every run of non-`[a-z0-9_-]` with `_`. So `Jude's S25 Ultra` becomes
> `jude_s_s25_ultra` — three underscores, and the label dies at the first. **Hyphens survive the
> sanitizer.** `S25U` → `s25u`, `Tab-S10FE` → `tab-s10fe`, both underscore-free, both surviving the
> shortener intact as `android-synced-from-s25u`.
>
> ⚠️ **Renaming is not free — the hostname is the sync directory name.** After a rename the device
> publishes to `<newname>/<uuid>/` and the **old directory stays in the shared folder forever**,
> re-imported as a phantom peer by every device on every cycle (this is the 1.4a failure mode
> again). Buckets already synced under the old name also keep it. Do it once, on both devices,
> then delete the old directories by hand.

**The local bucket carries no hostname either way** (`aw-watcher-android`, shown as `android`) —
that is inherent to the id, not the shortener. On any device, plain `android` is *that* device.

**Where it came from.** `shortenBucketLabel` and `formatTimelineBucketLabelHtml` were added by
[aw-webui#757] (merged 2026-02-22, `1393ec6d`) to fix [aw-webui#682] — a user with a 62-character
cloud hostname whose synced bucket names made the timeline sidebar unusable. The intended output
was `aw-watcher-window_host-synced-from-remote` → `aw-watcher-window (synced from remote)`. That is
the **desktop** id shape, and it is the only one the sync branch handles.

**There is a partial safety net, and it does not fire here.** `formatTimelineBucketLabelHtml` takes
an optional `hostname` and renders `short @ host` — but `VisTimeline.vue` only passes it when
`hasCollision` is true, i.e. when two buckets shorten to the *same* label. On a two-device setup
every shortened label is still unique, so nothing is appended and the truncated name stands alone.
Ironically the label **self-heals at three devices**: two peers whose names both begin `Jude`
collide, and both then get `@ <full hostname>`.

⚠️ **Still present on upstream `master` as of 2026-09-03** — fetched and compared byte for byte
against our pin; the file is identical. **No existing issue covers it**: searched the ActivityWatch
org for `shortenBucketLabel`, `timeline label truncated`, `synced-from`, and `hostname bucket name
display`. The nearest prior art is #682 (closed, the origin) and [aw-server-rust#649], which
proposed *"keep the raw ID in a tooltip, stop using it as the label"* — that is Stage 2 of its
plan, and **our pinned aw-webui has none of it** (`grep` for `sync.origin` in `src/` returns
nothing).

> ✅ **There is a working way to tell devices apart today, no rename needed.** The Timeline has a
> **Host filter** (`Filters ▸ Host:`) built from each bucket's `hostname` **metadata**, not its id
> — so it is never truncated. It lists `jude_s_tab_s10_fe` and `jude_s_s25_ultra` in full. Select
> one and only that device's rows remain. This is the reliable answer to *"which row is the S25U?"*
> until the label is fixed.

**Verified by running the real code, not by reading it (2026-09-03).** `origin/master`'s
`timelineLabels.ts` was extracted with `git show` and executed under Node against the tablet's live
bucket list, reproducing `VisTimeline.vue`'s collision logic verbatim. Result: `hasCollision =
false`, and **4 of the 5 synced buckets lose their origin hostname**. Both files are byte-identical
between our pin and `origin/master` (`git diff --stat` empty), so this is a live master bug.

**Candidate fix, and an honest note about what it changes.** Relax the sync regex so it does not
require an underscore, then shorten the base:

```js
const syncMatch = bucketId.match(/^(.*?)-synced-from-(.+)$/);
const base = shortenBucketLabel(syncMatch[1]) || syncMatch[1];
```

| Bucket id | master today | with the fix |
|---|---|---|
| `aw-watcher-android-synced-from-jude_s_s25_ultra` | `android-synced-from-jude` | `android (synced from jude_s_s25_ultra)` |
| `aw-stopwatch-synced-from-ad0c6c34-…` | `stopwatch-synced-from-ad0c6c34-…` | `stopwatch (synced from ad0c6c34-…)` |
| `aw-watcher-window_erb-m2.localdomain-synced-from-remote` | `aw-watcher-window (synced from remote)` | `window (synced from remote)` |

⚠️ **The desktop row changes too — `aw-watcher-window` becomes `window`.** An earlier draft of this
claimed desktop was untouched; that was wrong, and running the code is what caught it. The change
is arguably right (the *non*-synced desktop label is already `window`, so this makes the two
consistent) but it is a change and must be declared when reporting. The alternative that keeps the
base verbatim regresses #682 — it puts the 62-character hostname straight back into the label.

> ### 🔺 It is not an Android bug, and that answers "why fix it in aw-webui?"
>
> Reasonable objection: aw-android exists to absorb platform differences so shared components do
> not have to care about Android. So why is this fixed upstream in aw-webui rather than here?
>
> **Because the trigger is the bucket-id *shape*, not the platform.** `shortenBucketLabel` cuts at
> the first `_`. Desktop watcher ids embed the hostname with an underscore
> (`aw-watcher-window_host`), which *accidentally* satisfies the `syncMatch` regex, so they escape.
> The bug hits any bucket whose **base id contains no underscore**. Verified by running master's
> real code:
>
> | Bucket id | Label | |
> |---|---|---|
> | `aw-watcher-window_host_a-synced-from-host_b` | `aw-watcher-window (synced from host_b)` | ✅ desktop watcher, fine even with underscored hosts |
> | `aw-watcher-afk_my_pc-synced-from-other_pc` | `aw-watcher-afk (synced from other_pc)` | ✅ fine |
> | **`aw-stopwatch-synced-from-my_desktop`** | **`stopwatch-synced-from-my`** | ❌ **broken — and this is desktop** |
> | `aw-watcher-android-synced-from-my_phone` | `android-synced-from-my` | ❌ broken |
>
> **`aw-stopwatch` is aw-webui's own feature** (`client: aw-webui`), present on every platform.
> Two desktops with underscored hostnames syncing stopwatch data hit this with no Android involved.
> So it is a defect in a shared display helper that Android merely exposes most often — Android's
> hostnames come from a device name whose spaces become underscores, making it the common case
> rather than the exception.
>
> The division of labour this fork follows is about **runtime and platform** concerns — JNI, SAF,
> the WebView, Android services. `aw-webui` is deliberately *shared* UI, bundled unmodified into
> the APK. Patching it here would mean forking a third repository
> ([`07_OPEN_QUESTIONS.md`](07_OPEN_QUESTIONS.md) Q4 says no) and would leave desktop broken. The
> only fork-side "fix" available is renaming devices to avoid underscores, which is bending our
> data to suit someone else's display bug.

**Reproducible without sync — verified on the tablet 2026-09-03.** Asking a maintainer to pair two
Android devices is a good way to have a bug ignored, so the repro was reduced to a single JSON
import. Read out of `aw-server/src/endpoints/import.rs`:

```rust
for (_bucketname, mut bucket) in import.buckets {
    match datastore.create_bucket(&bucket) {
```

The map key is **discarded** (`_bucketname`); the bucket is created under `bucket.id` from the
payload, so any id can be minted directly — no watcher, no sync. Two details that matter and are
only visible in the source:
- `id` carries `#[serde(default)]`, so **omitting it creates a bucket named `""`** rather than
  falling back to the map key. It must be written out explicitly.
- `DELETE /api/0/buckets/<id>` is unconditional (`bucket_delete`, no force flag), so the repro is
  fully reversible.

Round-tripped against the live server twice. The **minimal** payload that works — no `created`, no
`data`, no `last_updated`, and a deliberately wrong map key:

```json
{ "buckets": { "b": {
  "id": "aw-watcher-android-synced-from-my_phone",
  "type": "currentwindow", "client": "aw-android", "hostname": "my_phone",
  "events": [ { "timestamp": "2026-09-03T10:00:00Z", "duration": 600,
                "data": { "app": "Firefox" } } ] } } }
```

`POST /api/0/import/` → **HTTP 200**. No bucket named `b` was created — it landed as
`aw-watcher-android-synced-from-my_phone`, which is the map key being discarded, proven rather than
inferred. `created` was filled in by the server. The real `timelineLabels.ts` then rendered it
**`android-synced-from-my`**, and `DELETE` removed it cleanly (10 buckets before, 10 after).

**Not fixed here.** A fork-side fix means patching aw-webui, which
[`07_OPEN_QUESTIONS.md`](07_OPEN_QUESTIONS.md) Q4 decided against. Report it upstream instead.

[aw-webui#757]: https://github.com/ActivityWatch/aw-webui/pull/757
[aw-webui#682]: https://github.com/ActivityWatch/aw-webui/issues/682
[aw-server-rust#649]: https://github.com/ActivityWatch/aw-server-rust/issues/649
[aw-webui#960]: https://github.com/ActivityWatch/aw-webui/pull/960

### 1.9 — A sync that failed must not report success ✅ VERIFIED ON DEVICE 2026-09-03 (failure path 2026-09-08)
Found by reading upstream **[PR #251]**, which fixes the same class of bug: *"a failed SAF mirror
was also logged as non-fatal, so the app could report native sync success while the user-selected
directory stayed unchanged."* True of this fork too, in both directions — and the import side is
worse, because a failed import is exactly the *"sync works but the other device never appears"*
symptom that cost this project most of Phase 1.

**The root cause was a data structure, not a `catch` block.** Both passes tallied into
`counts = intArrayOf(0, 0) // [copied, skipped]`, and `skipped` was incremented for two unrelated
things: a file that was already current, and a file that *failed to copy*. `importFile` said so in
its own contract — *"@return true if bytes were copied; false if the file was already current, **or
on any failure**"*. A boolean cannot carry three states. So a pass where every single file failed
logged `copied=0 skipped=1`, which is byte-identical to a healthy pass with nothing to do.

Four changes, in dependency order:

| Change | Why |
|---|---|
| `FileOutcome { COPIED, SKIPPED, FAILED }` replaces the `Boolean` | the three states the code always had |
| `TransferResult` (copied/skipped/failed/peers + first error) replaces `IntArray(2)` | separates *deliberate* from *broken*, and keeps a reason worth showing a user |
| `runTransfer()` converts a throw into a failed result | still catches — one bad pass must not abort a cycle that can finish — but the failure travels back instead of dying in logcat |
| export moved **before** the callback | an export that runs *after* the caller is told "success" can never correct that answer |

That last one deleted `syncBothAndMirrorAsync`, whose only reason to exist was running the mirror
before the callback for background workers. Now that every caller does, one entry point remains.

**Failures are collected, not thrown.** A failed import still leaves the native sync worth running
— peer databases from an earlier cycle are on disk and readable — so the cycle finishes and reports
everything it hit. Native pull/push failures still end the cycle, because nothing after them can
succeed.

**Result:** `Sync failed: export failed: sync folder unreachable (permission revoked, or folder
deleted)` now reaches the Sync Settings status line, where it used to read `Sync complete`. Logcat
gains a `failed=N` column and logs an unsuccessful sync at **warning** level rather than info.

⚠️ **Deliberately unchanged, so the next person does not think it was missed:** if the native pull
fails, the export is skipped, so our own data is not published that cycle. Arguably it should still
publish — peers would get our data even during a server problem. Left alone because a native pull
failure almost certainly breaks the push too, and the next cycle is 15 minutes away. Revisit if a
device is ever seen stuck.

✅ **Verified on both devices 2026-09-03**, from a CI build of `b12e398`. The healthy path is
unregressed and the ordering fix is visible in the timestamps — compare the same device before and
after:

```
OLD (phone, 12:26:09)   Multi-Device Sync completed: success=true   .310
                        SAF export: … copied=1 skipped=0            .334   ← export AFTER the callback

NEW (phone, 12:32:05)   SAF import: peers=1 copied=1 skipped=0 failed=0   .677
                        SAF export: … copied=1 skipped=0 failed=0         .852
                        Multi-Device Sync completed: success=true         .853   ← export BEFORE
```

The tablet shows the same shape at 12:28:12. `failed=0` is present on both passes, so the new
column is live and the success path still reports success.

✅ **The failure path is now verified too — on the tablet, 2026-09-08.** The status line reads
`Sync failed:` and both failure sites fire at **warning** level, so the whole of 1.9 is closed.

```
15:57:24.840  W  SAF directory not accessible or not a directory: …ActivityWatch-sync-MISSING
15:57:25.112  W  SAF directory not accessible or not a directory: …ActivityWatch-sync-MISSING
15:57:25.112  W  Multi-Device Sync completed: success=false, message=import failed: sync folder
                 unreachable (permission revoked, or folder deleted); export failed: sync folder
                 unreachable (permission revoked, or folder deleted)
15:57:25.113  I  Manual sync finished: success=false, message=…
```

On screen: `Sync failed: import failed: sync folder unreachable (permission revoked, or folder
deleted); export failed: sync folder unreachable (permission revoked, or folder deleted)`.

📝 **Correction to the Result above:** it predicted `Sync failed: export failed: …`. The real
message names **both** passes, joined by `; `. That is the collect-don't-throw design working as
written — the import failed, the cycle carried on to the export rather than aborting, and the user
is told about both. Worth knowing before anyone matches on that string.

⚠️ **Not exercised: a literal Settings-UI revoke.** Both sites guard on the same
`safDir == null || !safDir.isDirectory`, and this test hit it with a **stale `syncDirUri`** —
`…%3AActivityWatch-sync` → `…%3AActivityWatch-sync-MISSING` in `shared_prefs/AWPreferences.xml`,
via `run-as`, restored byte-identical afterwards. Same line, same `result.fail()`, same propagation
to `tvSyncStatus`; it is the *folder-unreachable* half of that `||`, not the *permission-revoked*
half.

**Why not the real revoke:** the sync folder is **Syncthing-managed** (`.stfolder`, `.stversions`).
Renaming or deleting it to force the failure would propagate the deletion to the phone. Do not do
it. The stale-URI swap touches no synced data at all — verified after the test: both peer databases
still present, folder 3.4M → 5.9M from ordinary syncing.

⚠️ **Also note: the `failed=N` column never appears on this path.** Both passes return at the
`safDir` guard, *before* the `SAF import:` / `SAF export:` lines that carry the counters. A folder
that is unreachable is reported through the message, not the counts — so do not grep for `failed=1`
to detect this class of failure.

🔧 **Gotcha that cost an hour, for whoever automates this next: `am force-stop` is not enough.**
`BackgroundService` is sticky, so Android restarts the process within a second or two — and if you
edit `shared_prefs` *after* the force-stop, that restarted process has already cached the old
values, and `monkey` then just refocuses it. The first attempt looked like the fix had silently
failed: prefs on disk said `-MISSING`, the app logged the real URI and reported `success=true`.
**Correct order: edit prefs first, then force-stop, then confirm a new pid appeared, then drive
the UI.** `SyncSettingsActivity` is not exported, so reach it with
`monkey -p … -c android.intent.category.LAUNCHER 1`, then the drawer, then *Sync Settings*.

**The healthy path still works after restoring** — same session, `16:01:19`:
`SAF import: peers=1 copied=0 skipped=1 failed=0`, `SAF export: … copied=1 skipped=0 failed=0`,
`success=true`, export still logged **before** the completion callback.

[PR #251]: https://github.com/ActivityWatch/aw-android/pull/251

---

### 1.11 — Bump `aw-webui` past the #960 fix ✅ VERIFIED ON DEVICE 2026-09-04
The step [1.8](#18--emit-a-title-on-window-events-so-the-activity-view-is-not-empty)'s caveat and
the 4.2% ceiling both end here, and neither needed any code of ours: upstream's
[aw-webui#960] (`85db7b5`) stopped merging `aw-watcher-android` buckets on the `title` key, so
pre-1.8 events stop being invisible to the Activity query. The events were **always synced** — only
the dashboard was hiding them.

**What moved.**

| Repo | From | To |
|---|---|---|
| `aw-webui` (submodule of aw-server-rust) | `3cbe349` | `a2ca625` — current `origin/master` |
| `Judemasic/aw-server-rust@beta` | `c6f7df2` | `9e01fab` |
| `Judemasic/aw-android@beta` | `bd39674` | `1524318` |

`3cbe349` was a clean ancestor of `origin/master` (`git log origin/master..3cbe349` is empty), so
this fork carries **no aw-webui changes of its own** and the move is a fast-forward, not a merge.
Eleven commits, of which three matter here:

- **`85db7b5`** (#960) — the fix this step exists for.
- **`2809ee2`** (#966) — replaces `prompt()` with modals for Android WebView compatibility.
  `prompt()` is a no-op in a WebView, so anything that used it was silently dead on this app.
- **`97891e1`** (#956) — category JSON import from Android SAF.

Taking master's head rather than `85db7b5` exactly is deliberate: the two extra Android fixes are
free here, and stopping short of them would mean bumping again shortly.

**The push order was not optional this time** — the submodule actually moves, so
`aw-server-rust@beta` was pushed **first** and the pointer second ([`02`](02_ARCHITECTURE.md) §5,
cache trap). A pointer-only push gives CI a cache hit and an APK with stale `.so` files.
CI build [33874769211] was dispatched from `beta` at 12:49 UTC.

✅ **CI is green ([33874769211], 21m30s) and the APK provably carries the new aw-webui.** The build
took twice the usual 10–17m, which is itself the cache miss doing what it was supposed to. The
stale-`.so` worry was then closed directly rather than assumed away: aw-webui is embedded in
`libaw_server.so`, so the artifact was downloaded, `lib/arm64-v8a/libaw_server.so` extracted, and
the query string the fix introduces searched for.

```
grep -c 'merge_events_by_keys(events, ["app"]);'  libaw_server.so   → 2
```

That exact string exists **only** at the new pin — `git grep` finds it at `a2ca625:src/queries.ts:173`
and nowhere in `3cbe349`. (`["app", "title"]` is still present too, as it should be: #960 keeps it
on the `isIos` branch for ScreenTime buckets.) So the delivery path is verified end to end, and the
one remaining unknown is genuinely the behaviour, not the build.

✅ **VERIFIED ON BOTH DEVICES 2026-09-04.** The owner installed the build on both (phone
`lastUpdateTime 15:29:32`, tablet `15:32:32`), and both installed APKs carry the fix — grepping
`base.apk` on each device for the post-fix query string returns **8** (two hits per embedded
webui, across the arm64 and x86_64 `libaw_server.so` / `libaw_sync.so`).

The measurement was then taken through each device's own server, running the two merge-key variants
over the full year so the *only* difference is the thing #960 changed:

| Device | Bucket | old `["app","title"]` | new `["app"]` | |
|---|---|---|---|---|
| **Tablet** | `aw-watcher-android-synced-from-jude_s_s25_ultra` | 48,186s | **399,383s** | **8.3×** |
| Phone | `aw-watcher-android` (its own) | 67,542s | **418,760s** | 6.2× |
| Phone | `…-synced-from-jude_s_tab_s10_fe` | 4,563s | **50,138s** | 11.0× |

The tablet row is this step's stated check. It predicted `~15,503s → ~366,700s`; the observed pair
is `48,186s → 399,383s`. **Both numbers are higher than predicted for the same reason** — two more
days of events accrued between the 09-03 measurement and this one, and the newer ones carry titles
(1.8), which lifts the *old* query's figure too. The ratio is the result, and no data was re-synced
to get it.

> ⚠️ **What this measures, precisely:** the query engine, over the real buckets, on the real
> devices — the same merge that `canonicalEvents` performs and that was returning near-zero. It is
> not a screenshot of the rendered Activity page. The rendering half rests on the installed APK
> containing the fixed webui, which the `base.apk` grep above establishes directly.

**Worth a look while you are in the app:** `Top Applications` for a day recorded *before* the 1.8
build — that is the specific thing #960 unhides, and it is the visual counterpart to the numbers
above.

[33874769211]: https://github.com/Judemasic/aw-android/actions/runs/33874769211

---

## Phase 2 — Shared state

### 2.1 — Shared folder layout + `VERSION` ✅ VERIFIED ON DEVICE 2026-09-09
Create `devices/<uuid>/`, write `meta.json`, add version read/refuse. *(R20)*

**Result.** `SharedFolder.kt` is new and owns all three. Every sync cycle now:

1. reads the root `VERSION` first, **writing `1` if the folder has none**, and **ends the cycle
   before the import** if it cannot be understood — the refusal [`05`](05_DATA_MODEL.md) §8 asks
   for, reported to the user as
   `sync folder refused: its VERSION was written by a newer version of ActivityWatch`;
2. rewrites `devices/<our uuid>/meta.json` wholesale after the export (**R20** — we are its only
   writer), carrying `display_name` (the hostname, so peers label us the way we label ourselves),
   `role` (`phone`/`tablet` by `smallestScreenWidthDp`), `platform`, `app_version`, `last_seen`;
3. skips `devices/` when walking the root for peer databases — without that it is treated as a
   hostname and every peer's `meta.json` is copied into app-private storage.

**⚠️ `events.db` did *not* move into `devices/<uuid>/`, deliberately.** [`05`](05_DATA_MODEL.md) §2
drew it there; aw-sync's `setup_local_remote` actually writes `<hostname>/<device_id>/test.db` and
`find_remotes` reads that same shape back. That is the layout Phase 1 verified on two devices, so
`devices/` was added **beside** the hostname directories instead, and §2 was corrected to match
what ships rather than the other way round.

**⚠️ The restore guard is half-built.** §7's debt (flagged by 1.1) is *detected*, not fixed:
`looksLikeForeignLineage` logs at error level when a `meta.json` under our uuid reports another
`platform`, but it cannot mint a new uuid — the uuid comes from the embedded server, not from
`AWPreferences` as §7 assumed, so re-minting needs a Rust call that does not exist. `app_version`
is deliberately not part of the test: it changes on every ordinary app update.

**Check:** `scripts/check-local.sh kotlin` and `:mobile:testDebugUnitTest` pass; 11 new tests in
`SharedFolderTest.kt` cover the version verdicts (absent / older / ours / newer / junk), the
`meta.json` round-trip and field names, unknown-field tolerance, and the lineage test's false
positives.

**Verified on device 2026-09-09**, both `SM-S938B` (phone) and `SM-X520` (tablet): a single
`VERSION` file at the shared-folder root containing `1`, `devices/<uuid>/meta.json` present for
both devices with correct `role` (`phone`/`tablet`) and fresh `last_seen`, no double-counted
peer from `devices/` being walked as a hostname.

**⚠️ Bug found and fixed during this pass — `VERSION` was never named `VERSION`.**
`createFile("text/plain", "VERSION")` hits SAF's local-storage provider mapping `text/plain` to a
preferred extension, so the file actually landed as `VERSION.txt`. `ensureVersion()`'s own
`findFile("VERSION")` then never found what it had written, treated the folder as version-less on
every single sync, and wrote another copy each time — three turned up
(`VERSION.txt`, `VERSION (1).txt`, `VERSION (2).txt`) after two devices ran a couple of syncs each,
all correctly containing `1` but under the wrong name. Fixed by creating it as
`application/octet-stream` instead, which has no preferred extension — the same MIME type
`mirrorDirectory` already uses for every other file it writes, for the same reason. `meta.json`
was unaffected because it already carries its own `.json` extension. Commit `5efe301`.

### 2.2 — Append-only JSONL store ✅ DONE IN CODE 2026-09-09 — nothing calls it yet
Read/write/merge for `decisions.jsonl` and `settings.jsonl`, including tombstones and the
deterministic tiebreak from [`05`](05_DATA_MODEL.md) §4.2. **Unit-test the merge with shuffled
input orders** — the property that matters is order-independence (R18), and it is easy to lose
without a test that specifically looks for it.

**Result.** `SharedStore.kt` is new and holds the whole model and merge, with no Android and no I/O
in it so the merge is unit-testable on the JVM:

1. **Records** — `decision`, `tombstone`, `setting`, and `Unknown`. `Unknown` keeps the raw line
   verbatim, which is [`05`](05_DATA_MODEL.md) §8's "ignored, not dropped": a line type a newer
   build wrote, *and* a line truncated mid-transfer, both survive us. Parse and serialise
   round-trip, one line per record.
2. **`mergeDecisions`** — dedupe by `id`, drop what any device's tombstone revokes, group by
   `(window, signature match key)`, keep the newest, ties on lowest `created_by`. The result is
   sorted, so the *list* is order-independent, not just its contents.
3. **`effectiveSettings` / `effectiveSettingValues`** — last write wins per key, same tiebreak,
   ordered by key. This is what 2.3 routes `category.*` through.
4. **`Ulid`** — 48-bit millisecond timestamp + 80 random bits, Crockford base32, `d_`/`t_`
   prefixes per §4. Ids sort chronologically as plain text and need no coordination to be unique.
5. **`SharedFolder.appendShared` / `readShared` / `readAllShared` / `listDeviceUuids`** — the SAF
   half. Appends go through SAF's `"wa"` mode with a read-and-rewrite fallback for providers that
   refuse it (safe only because we are our own file's only writer, R20); files are created as
   `application/octet-stream` for the same reason `VERSION` is, so nothing renames
   `decisions.jsonl` to `decisions.jsonl.txt`.

**Judgment calls**, both recorded in [`05`](05_DATA_MODEL.md) §4.2 because a device resolving them
differently would disagree with this one: a duplicated `id` counts once, and `id` is the final
tiebreak after `created_by` (same device, same millisecond, same group would otherwise be decided
by arrival order). A third: an unparseable `created_at` loses to every parseable one.

**Check:** `scripts/check-local.sh kotlin` and the full `:mobile:testDebugUnitTest` pass; 26 new
tests in `SharedStoreTest.kt`. Every merge assertion runs through `assertOrderIndependent`, which
re-merges the same lines under 50 fixed shuffle seeds and reversed, so an order-dependent merge
fails rather than passing by luck.

**Not verified on device, and there is nothing to verify yet:** no caller writes or reads these
files, so the app behaves exactly as it did before. 2.3 is this store's first on-device exercise.
Compaction (§5) is still not built.

### 2.3 — Settings sync ✅ VERIFIED ON DEVICE 2026-09-09
Route `category.*` / `label.*` through the shared store; keep device-local keys in `AWPreferences`.
**Check:** rename YouTube to "fun" on device A; device B shows "fun" after a sync. *(R25, R28)*

**Result.** Every sync cycle now has a step 7: publish the shared settings the owner changed on
this device, and apply the ones they changed on another.

1. **The keys are aw-webui's, not `category.<app>`.** aw-webui keeps the whole categorisation in
   one `classes` value and posts it whole, so renaming YouTube *is* an edit to `classes`. Routing
   its own keys avoids inventing a parallel key space and translating between the two forever.
   [`05`](05_DATA_MODEL.md) §5 is corrected to match, with the full allowlist and the reasoning for
   every key left out.
2. **An allowlist, in `SharedSettings.kt`.** Shared: `classes`, `category_sets`, `active_set_ids`,
   `privacy_filters`, `always_active_pattern`, `startOfDay`, `startOfWeek`, `durationDefault`, plus
   the `category.` / `label.` / `rule.` namespaces for Phase 4. Everything else stays local (R28) —
   including `views` and `saved_queries`, which name buckets whose ids carry a hostname and would
   point at nothing on the receiving device.
3. **`planSettingsSync` is pure**, so the whole decision is unit-tested: publish when the local
   value differs from what we last agreed to, otherwise accept the merge's winner.
4. **Two new JNI calls** in `aw-server/src/android/mod.rs`: `getSettings` (every stored setting,
   values **as raw strings**, deliberately unlike the HTTP endpoint) and `setSetting`. JNI rather
   than HTTP for the reason the existing `getSetting` gives — API-key auth is on by default, so an
   unauthenticated local GET 401s and reads as "no settings".
5. **`AWPreferences.appliedSharedSettings`** remembers the last-agreed value per key. Without it,
   "the owner changed this here" and "a peer changed it and we have not applied it yet" are
   indistinguishable, and each device would republish everything it accepted, forever.

**Judgment calls.** Sharing `startOfDay` / `startOfWeek` / `durationDefault` — they are not
categories, but they change what a day's totals *mean*, and Phase 3 sums across devices. Keeping
`views` and `saved_queries` local, for the bucket-id reason above. Reading the `.jsonl` files in
place rather than copying them first: **R24**'s copy-before-open rule exists because SQLite cannot
survive a mid-read replacement, and a line-oriented text file can — a torn read costs one truncated
line, which the parser keeps as `Unknown` and the next cycle reads whole.

**Check:** `scripts/check-local.sh` passes in all four modes — and `aw-server/src/android/mod.rs`
is now in the `syntax` mode's list, since it is cfg-gated exactly like `aw-sync/src/android.rs` and
2.3 put two new JNI functions in it. 13 new tests in `SharedSettingsTest.kt`; the scenario ones
feed each plan's result back in as the next cycle's state and assert the second cycle does nothing,
because a settings sync that republishes every cycle would look fine from outside while growing the
log forever.

**Verified on device 2026-09-09**, phone `SM-S938B` → tablet `SM-X520`, CI build `22a9d09`.
The owner renamed `Media > Video` to `Media > Fun` on the phone and saved. Both halves passed:

- **It propagates.** The phone logged `Published 8 changed setting(s): active_set_ids,
  always_active_pattern, category_sets, classes, durationDefault, privacy_filters, startOfDay,
  startOfWeek` at `12:00:40`. After Syncthing delivered the file, the tablet logged
  `Applied 'classes' from another device` (and the other seven) and its datastore then held
  `['Media', 'Fun']`. The tablet's settings store had been completely empty before this — it is
  the fresh-device adoption path, not just an overwrite.
- **It goes quiet, which is the half that could have failed silently.** The next sync on the
  tablet logged nothing at all: no `Applied`, no `Published`. The phone's `settings.jsonl` still
  held exactly 8 lines, and the tablet had still not created a `settings.jsonl` of its own — so it
  did not echo back what it accepted. `AWPreferences.appliedSharedSettings` held all 8 keys, which
  is what makes that true.

**⚠️ Found during the test: correct data on disk can sit unapplied for 15 minutes.** The tablet's
own sync cycle ran at `12:00:44`, four seconds *after* the phone published — but before Syncthing
had delivered the file. The next cycle was not due for a quarter of an hour, so the rename was
invisible on the tablet while the correct value sat in its sync folder the whole time. Nothing was
wrong with 2.3's routing; the trigger was simply too rare. Fixed immediately after, see below.

**A note for the next test, and a correction to an assumption made during this one.** Syncthing
preserves the *source's* modification time on the files it delivers, so `stat` on the receiving
device says when the sender wrote the file, not when it arrived. It cannot be used to establish
propagation timing, and briefly was.

### 2.3a — Apply settings while the app is open ✅ VERIFIED ON DEVICE 2026-09-09
Direct follow-up to the latency found verifying 2.3, at the owner's request ("it needs a sync after
the file's already on the tablet — trigger it automatically, no closing and reopening").

`SyncInterface.refreshSharedSettingsAsync` is 2.3's settings step on its own: no native call, no
database transfer, just the small text files read through SAF and any changed values written to the
datastore. It is skipped while a full sync is running, since that cycle ends with the same step.
`SharedSettingsRefresher` runs it immediately when the app is resumed and every 30s while it stays
on screen, and stops on pause — nothing polls in the background, where nobody is looking.

**⚠️ The first implementation of this used a `ContentObserver` and did not work.** It is worth
recording why, because it is the obvious design and it fails silently:

- Registering an observer on the sync folder's document tree **succeeds** — no exception — and it
  does fire, for writes by *this* app.
- Syncthing delivering `meta.json` from the phone produced **no** notification on `SM-X520` in the
  following 75 seconds, though the file demonstrably arrived (its mtime matched the phone's new
  one).
- Writing a file into the folder from `adb shell` — a different uid, like Syncthing — produced
  **no** notification in 40 seconds either.

`ExternalStorageProvider` does not notify document-tree observers about other apps' writes, and
Android has never promised it would. An observer that reports only our own writes tells us nothing,
so it was removed rather than kept as decoration. **A 30-second poll while visible is duller and
works.**

**Verified on device 2026-09-09**, both directions, using `startOfWeek` rather than a category so
the owner's own data was never the test subject (restored to `Monday` afterwards; the categories
were untouched throughout).

- **Publishes without a sync.** Changed on the tablet at `12:32:47`; the tablet logged
  `Published 1 changed setting(s): startOfWeek` at `12:32:52` with no sync cycle running.
- **Applies without a sync — the half that needed a clean window.** The first round proved nothing
  about applying: a scheduled sync on the phone fired one second before the poll would have and
  took the credit (`Applied 'startOfWeek' from another device`, `12:33:19`). The second round was
  run in a window where the tablet had no sync due for another 13 minutes: the phone published at
  `12:33:49`, the tablet applied at `12:34:22`, and the tablet ran **zero** sync operations in
  between. 33 seconds end to end, consistent with the 30-second poll plus Syncthing's delivery.
- **The race guard fires.** `Settings refresh skipped: a full sync is already running` appeared
  exactly where the poll collided with a cycle, which is what stops the two writing the same keys
  at once.

**Check:** `scripts/check-local.sh kotlin` and the full unit suite pass. Neither trigger is
unit-tested — both are Android lifecycle plumbing a JVM test cannot reach, which is precisely why
the first attempt's failure had to be found on hardware.

### 2.3b — Show when settings last synced ⬜ DECIDED 2026-09-09, NOT BUILT
Settings propagation is invisible: it works, and the only evidence is a logcat line. The owner
asked whether to spend a step on a manual trigger or on an indicator. **Decided: the indicator.**

- **No new button.** [1.7](#17--sync-settings-reachability-and-a-manual-trigger) already put
  **Sync Now** in Sync settings, and since 2.3 that cycle ends with the settings step — so the
  manual trigger the question asks for exists and already does the job. A second button for
  settings alone would be two controls doing one thing, and would need its own guard against
  colliding with the first.
- **What is actually missing is an answer to "did it work?"** — which today requires
  `adb logcat`. So: a line under Sync settings reading e.g. *"Settings: 8 shared, last applied
  12:34 from Tab-S10FE"*, plus *"never"* when nothing has ever been applied — the state that
  currently looks identical to a broken sync.
- **Where the values come from:** `syncSharedSettings` already knows all of it — the size of
  `plan.linesToAppend`, the keys in `plan.valuesToApply`, and the device that wrote the winning
  record. It needs storing in `AWPreferences` (device-local, [`05`](05_DATA_MODEL.md) §7) and
  rendering; no new sync machinery.

**Check:** change a category on device A; device B's Sync settings shows a fresh "last applied"
time within a minute of the file arriving, without opening logcat.

---

## Phase 3 — Combined timeline (read-only)

### 3.1 — Origin tagging at merge ✅ VERIFIED ON DEVICE 2026-09-09
Tag imported events with their source device UUID. *(R11 — raw data untouched)*

**Result.** Every event copied in from another device now carries `$aw.origin.device` in its
`data`, holding the **UUID** of the device that collected it. The tag is derived at merge time from
the directory the database was read out of — the shared folder is laid out
`<hostname>/<device uuid>/test.db`, so the path already knows the answer and the writing device
never has to stamp anything into its own events (**R11**).

1. **`origin_from_db_path`** (`aw-sync/src/util.rs`) is the one place that turns a path into a
   device. It returns nothing rather than guessing when there is no parent directory name: an
   untagged event can still be attributed from its bucket, a *wrongly* tagged one cannot be told
   apart from a correct one.
2. **`sync_run` carries the origin alongside each remote datastore**, because that is the only
   point where the file's path is still in hand. `sync_datastores`' existing `src_did` argument
   — until now always `None` on the import path — is what delivers it, so no public
   signature changed.
3. **`sync_one` tags each event as it is read**, before any of the three write paths (paged
   insert, last-page insert, boundary heartbeat), so none of them can forget to.
4. **Export is untouched.** A staging copy is this device's own first-hand data; a tag on it would
   reach a peer as provenance it never had. An existing tag is also never overwritten, so data
   relayed through a third device keeps its true origin.
5. **A latent panic went with it.** The "bucket hostname is `unknown`" fixup did
   `src_did.unwrap()`, and `src_did` was always `None` on import — one malformed bucket in a
   peer's database would have taken the sync down. It now warns and carries on.

**Judgment calls.**

- **The tag is a UUID, not a hostname**, and it is a *new* key rather than a reuse of the
  bucket-level `$aw.sync.origin` (which is a hostname, and builds the `-synced-from-<host>` bucket
  id). Sharing one key name for two kinds of value is how the pair would eventually be misread
  — and touching the bucket id would split history, which
  [1.10](#110--timeline-truncates-every-peers-name-at-the-first-_) already warns about.
- **On the event, not on the bucket.** Step ① of the pipeline in
  [`04`](04_COMBINED_TIMELINE.md) §2 flattens every device's events into one list, at which
  point the bucket is gone. Tagging the bucket would also have been half-useless in practice:
  bucket data is only written at creation, so the buckets already imported on the tablet could
  never gain it.
- **No backfill.** The merge resumes from the newest event it already holds and never revisits
  older ones, so the phone history already on the tablet stays untagged. Rewriting hundreds of
  thousands of existing rows on a phone to add a field that is recoverable another way is a bad
  trade — 3.2 resolves an untagged event through its bucket's `-synced-from-<hostname>` suffix
  and `devices/<uuid>/meta.json`'s `displayName`. See [`05`](05_DATA_MODEL.md) §6.

**Check:** `scripts/check-local.sh` passes in all four modes; `cargo test -p aw-sync` passes
(28 tests, 5 new). The new tests assert that imported events carry the origin, that the *source*
events are not modified, that staged/exported events carry no tag, and that an existing tag
survives a relay — plus two on the path→UUID derivation.

**✅ Verified on both devices 2026-09-09** (CI build [34343835497], `feb127e`), driven over adb:
after fresh phone activity was synced across, the tablet logged
`= Synced 1 new events, tagged origin ad0c6c34-d388-4ef0-b906-976bd760b22d` — the **phone's**
`device_id`, read straight off the phone, not the tablet's own `7b54cfe9-…`. Pulling the tablet's
`sqlite.db` confirmed it in *stored* data, not just the log: 16 imported events across all four
`-synced-from-jude_s_s25_ultra` buckets now carry `$aw.origin.device`, the only distinct origin
UUID in the store is the phone's, and the tablet's own first-hand `aw-watcher-android` bucket
(715 events) carries **zero** tags — a device never stamps its own data (**R11**). The
`! Bucket hostname/device ID was invalid` warning fired on the phone's push and the sync carried
on rather than panicking (the `src_did.unwrap()` fix, item 5). Both devices reported
`success=true`.

### 3.2 — Segmentation + classification ✅ DONE (2026-09-09) — done in code, no device test
Implement pipeline steps ①–③ from [`04`](04_COMBINED_TIMELINE.md) §2, **in Rust** so a future
desktop client and a future aw-webui view reuse it (R2, Q4). Include idle exclusion and the
minimum-duration threshold (Q1).

**Result.** A new workspace crate **`aw-server-rust/aw-combined`** holds the first half of the
combined-timeline pipeline as one pure function, `compute_segments(PipelineInput) -> Vec<Segment>`.
It takes every device's post-sync activity and idle events (the caller reads them out of its
datastore — this crate does no I/O and no clock reads) and returns a list of non-overlapping
**atomic** segments, each labelled `Settled` (0–1 device active) or `Contended` (≥2 devices). It is
not wired to anything yet; nothing calls it.

- **① `normalise`** flattens all buckets into one list of `(start, end, device, bucket_id, data)`
  intervals. It resolves each event's origin device in three steps, never dropping an event
  (**R19**): (1) the `$aw.origin.device` tag from 3.1 if present; (2) else the `-synced-from-<peer>`
  suffix of the bucket id — `<peer>` looked up in a hostname→UUID map, and **used verbatim on a
  miss**; (3) else this device's own UUID. The miss branch in (2) is deliberate, not a fallback:
  [1.5](#15--two-device-end-to-end-verification) recorded a real
  `aw-stopwatch-synced-from-<uuid>` bucket where upstream names the peer by UUID, not hostname — the
  map misses, the captured string already *is* the UUID, and using it keeps an unknown peer visible
  instead of silently folding it into the local device. ① then subtracts each device's idle
  intervals (an idle period can split one activity interval in two) and drops every zero-width
  interval — the Android unlock watcher emits `duration = 0` heartbeats that would otherwise plant
  a boundary covering nothing.
- **② `segment`** is a boundary sweep: collect every interval endpoint, sort, dedup, and for each
  adjacent pair emit a segment carrying every interval that fully covers it. Adjacent segments are
  **never merged** — §2.1 calls these *atomic*; an app change on any device is a real boundary, and
  merging on "same device set" would throw away one of two consecutive `data` maps. Coalescing on
  *identical attribution* is 3.3.
- **③ `classify`** marks a segment `Contended` iff it covers ≥2 **distinct** devices, then runs the
  minimum-duration pass **over contiguous contended runs, not single segments**: it finds each
  maximal run of temporally adjacent contended segments and, if the run's *total* duration is
  < 60 s (`DEFAULT_MIN_CONTENTION_SECS`, D15/Q1), demotes every segment in it to `Settled` with
  `absorbed_short_contention = true`. Exactly 60 s stays contended.

The **idle contract**: `PipelineInput.idle` is "events that are already known to be idle periods" —
the caller filters by its own AFK schema; this crate knows none. It is empty on Android, where
`aw-watcher-android` only records while the screen is on and in use, so its events already *are* the
active signal. A desktop caller populates it from `aw-watcher-afk`. This keeps the function
platform-agnostic (**R2**).

**Judgment calls** (neither dictated by `04` or the roadmap):

- **Minimum-duration threshold applied to runs, not individual segments.** Segments are cut at
  every app change on every device, so a genuine 15-minute contention where either device switches
  app every 30 s is shredded into sub-60 s atomic segments. Thresholding each separately would
  demote all of them and make real contention vanish — the opposite of **R7/R8**. The threshold is
  about short *episodes* ("walking between two devices"), which is a property of the run.
- **Short contention is demoted-and-flagged, not merged into a neighbour.** §2.2 says short
  contention "attaches to the neighbouring settled segment", which is undefined when both
  neighbours are settled with different activities, or when there is no settled neighbour at all.
  Demoting keeps the segment, its data, and determinism, and stops it being shaded or asked about —
  which is what §2.2 is for. ~~**Open point for 3.3:** whose activity a demoted
  `absorbed_short_contention` segment's time is ultimately credited to.~~ **Closed in 3.3:** it
  still goes through the same provisional pick as any other segment, so its time is credited to its
  longest-running activity — nobody's by decision, the longest activity's by placement. `state ==
  Settled` therefore does **not** imply ≤1 device — consumers must check `active`, not `state`.

**Check:** `cargo test -p aw-combined` — 29 tests, all green: 14 golden + 6 unit + 9 adversarial.
The golden tests assert the
`04` §6 worked example boundary-for-boundary, a three-device case (**R1** — a two-device
implementation passes a two-device test and still violates R1), all three origin-resolution
branches including both the hostname-in-map and hostname-missing cases and the 1.5 UUID-suffix
case, idle subtraction (removes an overlap; splits an interval), the run-threshold regression guard
(two adjacent 40 s contended segments, total 80 s → both stay contended), exactly-60 s, zero-width
drop, atomic-boundary preservation, and a determinism check that shuffles bucket and event order
and asserts identical output (**R18**). The **adversarial** set covers what the golden tests cannot
distinguish — that a time gap and that a settled segment each *end* a contended run (so two 40 s
halves are not wrongly summed to 80 s), that one device's idle never subtracts another's activity,
overlapping idle periods, idle swallowing an interval whole, a negative duration from a corrupt row,
a non-string origin tag falling through instead of panicking, and contention chained across three
devices counting as one run. `cargo test -p aw-sync` still passes (28 tests) — the
`EVENT_ORIGIN_KEY` constant moved from `aw-sync` to `aw-models` so `aw-combined` can read it
without depending on `aw-sync`; `aw-sync`'s public re-export is unchanged. `scripts/check-local.sh
rust` gained `cargo check -p aw-combined --lib`.

⚠️ **Known limitation — the sweep is O(n²) in event count.** ② tests every interval against every
boundary. Measured on desktop (release build, three devices): 3k events 18 ms, 6k 52 ms, 15k 253 ms,
30k 870 ms. A single day is a few thousand events, so the day view [3.4](#34--combined-view-with-shading)
builds is comfortable — but a **week or month view would need a sweep line** that carries a running
active-set instead of rescanning. Recorded here so 3.4 is not surprised by it; not worth fixing
before something asks for a multi-day range.

There is **nothing to test on a device** and nothing for the owner to do: this step is pure Rust
over fixed inputs, and nothing depends on the new crate yet. No CI build or APK is needed — the
`aw-server-rust` submodule pointer moves forward but carries only an unused crate.

### 3.3 — Provisional attribution ✅ DONE (2026-09-09) — done in code, no device test
Pipeline steps ⑤–⑥ with the deterministic tiebreak. *(R17, R18)*
**Check:** the same input yields identical output across repeated runs and across devices; totals
equal wall-clock (**R6**) — assert this directly, it is the invariant everything else rests on.

**Result.** `aw-combined` gained ⑤ `attribute` (run by `compute_segments` straight after ③) and ⑥
`coalesce` (a separate `pub fn` that `compute_segments` deliberately does **not** call). `Segment`
gained `foreground: usize` (an index into `active`) and `unresolved: bool`, with
`foreground_slice()` / `background_slices()` accessors for 3.4 to call.

- **Why `ActiveSlice` grew `source_start` / `source_end`, and why this is the important part of the
  entry.** `04` §2.4 rule 1 and **R17** both say "longest total duration in the segment wins". Read
  literally against the old types **that rule was dead code**: every slice in a segment covers the
  segment exactly (by construction in ②), so every candidate was the same length and *every* pick
  fell through to the UUID tiebreak — "lowest device UUID" would have been the only real rule, and
  every natural test would still pass. Rule 1 only means something if it compares the **originating
  activity interval** — the phone's hour-long YouTube session versus the tablet's fifteen-minute
  Kindle dip — so each slice now records that interval's span.
- **The total order** ⑤ applies, in sequence: (1) longest `source_end - source_start`; (2) lowest
  `device`; (3) lowest `bucket_id`; (4) lowest canonical `serde_json` string of `data`. Levels 3–4
  exist because rule 2 cannot separate one device's two overlapping buckets and `data` is not in
  ①'s sort key — without them the winner could depend on input order and break **R18**.
- **`unresolved` = `state == Contended`.** Provisional attribution changes *which* activity counts;
  it never clears the shading (**R8**). A segment demoted to `Settled` by the short-contention pass
  is `unresolved = false`.
- **`coalesce` is opt-in and lossy.** It merges adjacent segments whose **foreground `device` and
  `data` match** and whose flags all match, contiguous only. Keyed on the foreground pair, not the
  whole `active` vec: ActivityWatch splits one session into many heartbeat events carrying
  different source spans, so keying on `active` equality would merge essentially nothing and leave
  the view a wall of 30-second slivers. The merged `active` is the union of the parts', so
  per-instant background detail is gone — which is why `compute_segments` stays lossless and Phase
  4 keeps the atomic segments to attach decisions to.
- **Judgment calls.** (a) The recorded source span is **post-idle**, not the raw event span: a
  YouTube event that ran an hour with 40 minutes idle should not out-claim a tablet that read for
  25 straight minutes. (b) An `absorbed_short_contention` segment still gets the same provisional
  pick — see the next bullet.
- **The open point 3.2 handed forward is now closed.** "Whose time is a demoted
  `absorbed_short_contention` segment's?" — nobody's by decision, the **longest activity's by
  placement**. It is `Settled` so it is never shaded or asked about, but it goes through the same
  ⑤ pick as everything else, so its time is credited, not lost.

**Check:** `cargo test -p aw-combined` — **42 tests green** (6 unit + 18 golden + 15 adversarial +
3 invariants). The new `tests/invariants.rs` asserts **R6** as a property over several shaped
inputs: segments sorted and non-overlapping, exactly one valid `foreground` each, and
`Σ (end - start)` equal to the measure of the *union* of the post-idle activity intervals —
hand-computed (overlapping devices + an idle hole + a gap → 5700 s), explicitly *not* the sum of
the devices' durations — and that `coalesce` conserves it. Golden adds: rule 1 discriminates
against an opposing tiebreak (the losing device's UUID sorts lower on purpose), and `coalesce`
merges a heartbeat-split session while refusing to merge across an app change or a gap. Adversarial
adds: all three tiebreak levels, idle shortening a claim, `unresolved` tracking `Contended` not
device count, `coalesce` refusing to merge across a flag change, and determinism through
`coalesce` with slices tied on duration *and* device. `cargo test -p aw-sync` still green;
`cargo check --workspace` clean; `scripts/check-local.sh rust` passes. `aw-server-rust@beta` is
`a39f52e`.

**Nothing to test on a device and nothing for the owner to do** — pure Rust over fixed inputs, and
nothing calls the crate yet. No CI build or APK; the submodule pointer moves forward carrying only
the unused crate. The first Phase 3 device test is 3.4.

### 3.4 — Combined view with shading ✅ DONE (2026-09-09) — verified on both devices; UI superseded by [3.5](#35--rebuild-the-combined-timeline-in-aw-webui)
Render the combined track above per-device tracks; shade unresolved contention. *(R8)*
**Q4 is resolved (2026-09-02): native, phone-first, on top of the Rust pipeline from 3.2.** An
aw-webui view comes later for desktop. Born mobile-first per **R33** — it never joins Phase 5's
backlog.

**Result.** `aw-combined` is finally wired to something. A **Combined timeline** entry in the nav
drawer opens `CombinedTimelineActivity`: one day at a time, prev/next/today, the combined track
drawn above one row per device, unresolved contention striped and outlined.

- **`aw-server/src/combined.rs`** (new, in `aw-server-rust`) is the datastore adapter — the only
  place that knows both halves. It reads a range out of the datastore, sorts bucket ids for
  determinism (**R18**), splits them into activity and idle, runs `compute_segments` then
  `coalesce`, and returns JSON: the combined rows (foreground label, device, state, `unresolved`,
  background list), the raw per-device rows, and the totals.
- **It is deliberately not inside `android/`.** That module is `cfg(target_os = "android")`, so a
  desktop `cargo check` never type-checks a line of it — and this repo's local check *is* a desktop
  check. Keeping the logic in `aw-server/src/combined.rs` means the part that can be wrong is
  verified before an APK exists; the JNI function is a string-in/string-out shell. `check-local.sh
  rust` gained `cargo check -p aw-server --lib` to cover it.
- **JNI:** `Java_..._RustInterface_getCombinedTimeline(start, end, hostnameToUuidJson)`. Timestamps
  are RFC 3339 and the own-device uuid is read on the Rust side from aw-server's own `device_id`
  file, so the two halves cannot disagree about who "we" are. A bad argument returns
  `{"error": ...}` rather than panicking across the FFI boundary.
- **Kotlin:** `models/CombinedTimeline.kt` parses the JSON (Android-free, so it is unit-testable),
  `views/CombinedTimelineView.kt` draws it, `CombinedTimelineActivity` owns the day picker and the
  summary line. The JNI call is blocking and runs on `Dispatchers.IO`.
- **The summary line is the R6 demonstration:** it shows the combined total *against* the sum of
  the devices' totals. On a day with real overlap the combined figure is the smaller one, and that
  gap is the entire point of the feature.

**Judgment calls** (not dictated by `04` or the roadmap):

- **Activity is `currentwindow` only; `web.tab.current` is excluded.** A device with a web bucket
  has it overlapping its own window bucket for the same instants, so including it would put a
  browser *tab* in contention with its own *window* on one device and let a tab title win the
  combined track. Web data stays in the raw per-device view. Revisit if tab-level detail is wanted.
- **Per-device rows are built from raw events**, before idle subtraction and before segmentation,
  because **R11** says those rows are unmodified truth kept underneath for comparison. They call
  `aw_combined::resolve_device` (newly public) so origin is decided by the same rule (**R19**) the
  pipeline uses rather than a second copy that can drift.
- ~~**The hostname→uuid map is passed empty (`{}`)** … a peer with pre-3.1 history may appear as
  *two* rows … That is honest, not a totals bug (they never overlap in time).~~ **⚠️ This was
  wrong, and the device test proved it — see "What the hardware found" below.** The map is still
  passed empty, but the split it caused is now fixed at the source.
- **Shading is diagonal stripes, not a lighter tint.** A tint reads as "less of this activity",
  which is the opposite of what the flag means — the block's time *is* counted (**R17**); what is
  uncertain is which competitor deserved it.
- **`min_contention` is still the 60 s default, not a setting.** Nothing in the app exposes one and
  3.4 is about seeing the track at all. It gets a home when Phase 4 gives it one (D15/Q1).

**Check (local, done):** `scripts/check-local.sh` green — Kotlin compiles, host Rust checks clean
including the new `aw-server --lib`. `cargo test -p aw-combined` 42 green, plus 3 new `aw-server`
unit tests for the label/idle helpers.

**What the hardware found.** Two real bugs, neither reachable from a type check:

1. **The Activity crashed on every launch.** `private var day: LocalDate = LocalDate.now()` is a
   property initialiser, so it ran inside `<init>` — before `onCreate`, and therefore before
   anything could call `AndroidThreeTen.init`. Result:
   `ZoneRulesException: No time-zone data files registered`. This app has no `Application`
   subclass; every entry point registers the timezone data itself (`CategoryTimeWidgetUpdater`,
   `NotifyWorker`) and this one did not. `day` is now `lateinit`, assigned in `onCreate` after an
   `AndroidThreeTen.init(this)` of its own.
2. **The tablet was contending with itself.** Tapping a shaded block gave *"Syncthing-Fork counted
   for 10m 00s — also running: Syncthing-Fork"*. The tablet appeared twice: as
   `jude_s_tab_s10_fe` (1h 23m) and as `7b54cfe9-ec39-4ec3-934c-67c81111d8e7` (15m 57s), which is
   that tablet's own `device_id`. **Both came from one bucket.** A `-synced-from-<peer>` bucket
   accumulates: events merged before 3.1 are untagged and fell to the hostname rule, events merged
   after carry `$aw.origin.device` and hit the uuid rule. Resolved *per event*, one bucket yielded
   two device strings over the same instants, `classify` counted two devices, and the view invented
   contention. Most of the 16 shaded blocks on that first screenshot were this, not real overlap.
   **Fixed by resolving origin per bucket** (`aw_combined::resolve_bucket_device`): a bucket only
   ever holds one peer's events, so one tagged event settles all of them. No hostname map and no
   schema change needed, and it heals existing databases with no migration — which the map
   approach would *not* have, since a map only helps once every device has rewritten its
   `meta.json`. Three regression tests in `adversarial.rs`, the first reproducing the symptom.

**What "multiple lines" means and why it does not bite.** One device produces several concurrent
bucket streams — the S25U alone shows `android` (apps), `android-media` (playback) and
`android-synced-from-jude…` in the webui timeline. `android-media` is type `media.playback`, and
the adapter reads only `currentwindow`, so the phone does **not** contend with itself over music.
⚠️ **Consequence, recorded rather than hidden: music playing while another app is in front is
invisible in the combined track.** Whether "Spotify while reading" is one activity or two is a real
design question this step answered conservatively without asking.

**✅ Verified on both devices 2026-09-09** (CI build [34366963972], `9f6f855`), driven over adb.
Opened from the nav drawer on each, no crash on either.

| | Phone `SM-S938B` (17:15) | Tablet `SM-X520` (17:17) |
|---|---|---|
| Combined total | **7h 50m** | **8h 15m** |
| Sum of device totals | 9h 29m | 9h 53m |
| Devices listed | **2** (was 3 before the fix) | **2** |
| Shaded blocks | 16 | 15 |

- **R6 holds on both:** the combined total is below the sum of the devices' totals — 7h 50m < 9h 29m
  and 8h 15m < 9h 53m. That gap (1h 39m / 1h 38m) is the double-counting the whole feature exists
  to remove.
- **The self-contention fix is confirmed by arithmetic, not just by the count dropping.** Before,
  the tablet appeared as two rows of 15m 57s and 1h 23m; after, it is a single row of exactly
  **1h 39m** — their sum.
- **The remaining shaded blocks are real.** Tapping them still gives *"Syncthing-Fork counted for
  10m 00s — also running: Syncthing-Fork"*, but with only two devices left that is now the truth:
  the same app genuinely was foreground on the phone **and** the tablet at once. One block reported
  three slices across two devices (*"… also running: Syncthing-Fork, One UI Home"*), which is the
  phone's two `currentwindow` buckets — `SessionEventWatcher` and `UsageStatsWatcher` both create
  one — correctly counted as one device.

⚠️ **The two devices disagree, legitimately.** The phone credits the tablet 1h 39m while the tablet
credits itself 4h 46m, because each device only holds as much of its peer's history as its last
sync brought over. **R18 promises identical output for identical *input*** — it does not promise two
devices mid-sync agree, and this is not evidence against it. Worth remembering before reading a
disagreement as a bug.

⚠️ **UI: known bad. The fix is [3.5](#35--rebuild-the-combined-timeline-in-aw-webui), which
rebuilds this screen in aw-webui rather than restyling it** (owner, 2026-09-09: *"the ui is so so
bad"*, then *"i expect top of the line ui not just a few adjustments … the ui should worl o pc
table and phone"* — and a native Android `View` cannot run on a PC, see **R35**). The defect list
below is not wasted: it is the acceptance list 3.5b must clear. Worst first:

1. **Device names are raw UUIDs**, and on the phone the name **collides with the duration text**
   (`ad0c6c34-…-976bd760b22d (this devic7h)23m`). The tablet has the width to avoid the collision,
   so this is narrow-width layout, not a data problem.
2. **The tap detail names apps but not devices**, so genuine cross-device contention reads as
   nonsense: *"Syncthing-Fork — also running: Syncthing-Fork"* is correct but unreadable. It must
   say *which device* each side was on.
3. No legend — the colour of a block is unexplained until tapped.
4. Oversized default `‹` / `›` / `TODAY` controls; a paragraph where the summary wants figures.
5. The tablet rendering is already decent (see the 17:17 screenshot); the work is at phone width.

[34366963972]: https://github.com/Judemasic/aw-android/actions/runs/34366963972

---

### 3.5 — Rebuild the combined timeline in aw-webui ⏳ IN PROGRESS (2026-09-09) — HTTP endpoint done
*(**R35**, **R36**, R6, R8, R11)*

**This replaces the "UI pass" 3.4 owed.** The owner raised the bar on 2026-09-09 and, in doing so,
changed *where the screen has to live*:

> *"if you are gonna do the ui then i expect top of the line ui not just a few adjustments i will
> spendd most of my time in this app in this screen also i do like the acttivity veiw can wwe have
> thatt for the compind too? also dpnt forget thtat the ui should worl o pc table and phone"*

Three requirements, now written up as **R35** and **R36** in
[`01_REQUIREMENTS_AND_RULES.md`](01_REQUIREMENTS_AND_RULES.md):

1. **Top of the line, not a few adjustments** — this is the screen the owner lives in.
2. **Look like aw-webui's Activity view**, applied to combined data.
3. **Work on PC, tablet and phone.**

#### ⚠️ The finding that decides the architecture

**Requirement 3 rules out the 3.4 native screen, and no amount of layout work rescues it.**
3.4 drew the timeline with a hand-written Android `View` (`CombinedTimelineView.kt`). There is no
Android on a PC, so that screen can never satisfy R35 — and requirements 1 and 2 point the same
way, because the Activity view the owner likes *is* aw-webui.

aw-webui is the right home and needs no new plumbing to reach the owner's three targets:

| | How aw-webui gets there |
|---|---|
| Phone | Already rendered in the app's WebView (`WebUIFragment.kt`) against the embedded server |
| Tablet | Same WebView |
| PC | The same Vue app, served by desktop aw-server, in a browser |

So 3.5 is **not** a restyle of the native view. It is a rebuild of the screen as an aw-webui view,
reusing the Activity view's own components so it inherits that look rather than imitating it.

#### 3.5a — Serve the combined timeline over HTTP ✅ DONE (2026-09-09)
aw-webui speaks HTTP and **cannot call JNI**, so nothing else in 3.5 can start until the pipeline is
reachable over the API. New `aw-server/src/endpoints/combined.rs`:

```
GET /api/0/combined/timeline?start=<rfc3339>&end=<rfc3339>&hostnames=<json>
```

It is a thin wrapper — all the work stays in `aw-server/src/combined.rs`, which 3.4 already built
and which is shared with the JNI path, so **the two callers cannot drift**. `own_device` is read
server-side from `ServerState.device_id` rather than taken as a parameter, because the server
already knows and a caller could only get it wrong. `hostnames` stays a parameter: on Android that
map lives in the Syncthing folder behind SAF, which only Kotlin can open.

**Checked against a running server** (`--testing`, loopback), not just compiled:

| Case | Result |
|---|---|
| Empty datastore | `{"combined":[],"combined_seconds":0,"devices":[]}` |
| Two devices overlapping 30 min | **`combined_seconds` 5400, device totals 7200** — **R6 holds** |
| The overlapping half-hour | `state=contended`, `unresolved=true` — **R8 holds** |
| `start=nonsense` / `end` ≤ `start` / bad `hostnames` JSON | `400` each, naming the parameter |
| `hostnames` supplied | peer row shows `hostname=tablet` instead of a bare uuid |

⚠️ **The JNI entry point and the 3.4 native screen deliberately stay for now.** Deleting a working
screen before its replacement exists would leave the owner with none. They come out in 3.5c.
*(Done — 3.5c removed both on 2026-09-09.)*

#### 3.5b — The Vue view ✅ DONE (2026-09-09) — browser **and** device
Build `CombinedTimeline.vue` in aw-webui, reusing the Activity view's components so it inherits the
look. Must fix, at minimum, every defect listed under 3.4 — device **names** not raw uuids, a tap
detail that says *which device* each side was on, a legend — and must read well at phone, tablet and
desktop width (**R35**).

**Check:** the same day renders in a desktop browser and in the app's WebView on both devices; the
R6 summary matches the native screen's numbers for the same day.

##### What was built

In **`aw-server-rust/aw-webui`** (branch `beta`, commit `cb3b0c3`):

| File | What it is |
|---|---|
| `src/visualizations/ProportionalTimeline.vue` | **New, reusable, and deliberately generic.** Takes `tracks`, knows nothing about devices or contention, and carries `orientation` as a **prop**. This is the component [5.5b](#55--make-the-aw-webui-timeline-usable-at-phone-width) reuses and the one that could go upstream. |
| `src/views/CombinedTimeline.vue` | The view: fetches `/api/0/combined/timeline`, Mode/Range, device picker, rename, tap detail. |
| `src/stores/settings.ts` | New `device_names` key — renames persist **server-side**, not in localStorage, so a rename survives a reinstall and reaches the peer. |
| `src/route.js`, `components/Header.vue`, `i18n/locales/en.ts` | Route `/combined`, nav entry, string. |

**The combined view passes `orientation="vertical"` at every width**, not `auto`. Extra width buys
columns and a side detail panel, never a different axis — one layout that scales beats two that
drift. The component still supports horizontal because 5.5b needs it: upstream's desktop Timeline
users *do* expect a horizontal axis.

##### ✅ What was verified, against a running server

Not "it compiles". `aw-server --testing --webpath …/dist` was run with **three** seeded devices and
the page driven in a browser:

| | Result |
|---|---|
| **R6** | combined **368 min** vs device sum **608 min** — holds |
| Segments | 11, of which **4 unresolved** |
| **Worst contention** | a segment with **4 slices** (`Android Studio + YouTube + Kindle + Firefox`) — renders legibly as four labelled bands |
| Wide (1148px) | vertical axis, three **full labelled** device columns, detail panel beside |
| Narrow (510px) | vertical axis, 22px labelled gutter columns, detail below |

Three bugs were found this way and fixed, none of which a compile would have caught: gutter headers
rendered **white-on-dark** (a hardcoded `#fff` fallback); narrow gutter stripes **leaked duration
text** into 22px (`tiny` measures the time axis, not the cross axis); and adding a nav entry pushed
the absolutely-centred navbar brand into the left nav at ~1180px, so the brand moves to `xl`.

##### ⚠️ What is NOT verified

- **Nothing has run on a phone or tablet.** This is a browser result only.
- **True phone width was never reached.** Headless Edge has a hard **minimum viewport of 510px**
  (`--window-size=412` still reports `innerWidth=510`), so 412px is untested. The vertical layout is
  exercised at 510, but the narrowest case is not.
  ⚠️ **A correction:** an earlier note here claimed a screenshot of the stock Home screen *confirmed*
  aw-webui overflows sideways at phone width. That was unsound — it was the same 510px artifact
  being cropped to 412. [5.1](#51--audit-what-actually-breaks--mostly-done-2026-09-02)'s
  on-device evidence for overflow still stands; that particular screenshot proved nothing.
- **Device names show raw uuids by default.** The view does not pass the optional `hostnames` map,
  so an untagged peer has no friendly name until renamed. Rename works; the default is poor.

##### ✅ Unblocked: the `aw-webui` fork exists

`Judemasic/aw-webui` was created 2026-09-09 and `beta` (`cb3b0c3`) pushed to it. The submodule
chain now resolves entirely against the fork:

| Repo | `beta` commit | Records |
|---|---|---|
| `Judemasic/aw-webui` | `cb3b0c3` | the Combined view + `GET /api/0/combined/timeline` webui side |
| `Judemasic/aw-server-rust` | `57a29f6` | `.gitmodules` → `Judemasic/aw-webui`, submodule pinned to `cb3b0c3` |
| `Judemasic/aw-android` | (this commit) | `aw-server-rust` submodule bumped to `57a29f6` |

In the `aw-webui` checkout, `origin` is the fork and `upstream` is `ActivityWatch/aw-webui`, so
`master` still tracks upstream for merges. CI clones `submodules: recursive` and reads the fork URL
from each `.gitmodules`, so a CI build can now contain this screen.

⚠️ **Local toolchain note:** `npm install` fails on **npm 12** (`EALLOWSCRIPTS` preparing the
`vue-d3-sunburst` git dependency). `npx npm@10 install` works, and CI is unaffected — the workflow
has no `setup-node`, so it uses the runner's npm 10.

##### The layout problem, and the decision *(owner, 2026-09-09: "there is not inogh horisintal spavce on a phone ofr a good tmeline … maybe make it virtical?")*

**The owner is right, and it is arithmetic rather than taste.** A phone gives ~340&nbsp;pt of usable
width. Spread 24 hours across it and **one hour is 14&nbsp;pt**, so a ten-minute session draws
**2.4&nbsp;pt** wide — narrower than a hairline, unlabelable, and far below a 44&nbsp;pt touch
target. No font size fixes this; the *block* is the problem, not the text in it.

**Decision: the time axis runs vertically at phone width.** The reason matters, because it decides
everything else: **vertical is the axis in surplus.** Scrolling down is free and native on a phone;
horizontal width is fixed and scarce. So time — which needs unbounded room — takes the free axis,
and the app name takes the scarce one, where text reads anyway. At 64&nbsp;px/hour the same session
is `11 × 250` instead of `340 × 2.4`. This is what every calendar app converged on, against exactly
this constraint.

**The layout study is checked in: [`docs/design/combined-timeline-layout-study.html`](design/combined-timeline-layout-study.html).**
Open it in any browser. It draws one example day both ways at 412 / 834 / 1180&nbsp;px from real
proportional data — not a picture, so the block sizes are the block sizes. Setting *Viewport =
Phone* and *Time axis = Horizontal* reproduces the failure above and prints the per-hour figure.

Four supporting decisions, all demonstrated in that study:

1. **Device tracks are a gutter, not equal columns.** **R11** keeps the raw per-device tracks
   available, but they are *reference*, not the thing being read. Three equal columns on a phone
   gives everything ~130&nbsp;pt and ruins all three. Narrow: a 10&nbsp;pt stripe per device answers
   *who was awake here* at a glance. Wide: the stripes become full labelled columns.
2. **Collapse quiet runs.** Most of a tracked day is nothing. Runs over ~25 minutes collapse to a
   single `2h 14m quiet` divider, taking the day from ~20 screens of scroll to about 3.
3. **Keep a minimap.** Collapsing costs the "whole day at once" that horizontal gives free; a fixed
   24-hour density strip above the scroll area buys it back and shows scroll position.
4. **Rejected: a chronological card list.** It reads beautifully on a phone and is a fraction of the
   work, but it **kills the feature** — contention shading is a claim about *area* ("both devices
   were busy for this much of the same hour"), so dropping proportional time leaves **R8** with
   nothing to say. Proportional time is non-negotiable on this screen.

##### Acceptance list, from the owner's review of the layout study *(2026-09-09)*

The owner reviewed v1 (*"that look good"*) and named eight things. All eight are built into the
checked-in study and are **binding on 3.5b**:

| # | Requirement | Why it matters |
|---|---|---|
| 1 | **Every block tappable, not just the combined track.** Device events open a detail too. | v1 made the raw truth (**R11**) look like decoration. |
| 2 | **An `Edit` button carrying the original app's editor** — bucket, id, start, end, and the editable `data` keys, with Delete / Cancel / Save. | The owner already edits events in the original app and will not accept losing that. |
| 3 | **A contended block shows *both* apps in *both* colours**, split into a band per device — not a winner's colour with the other named in text. | v1 read as though one device was idle. The widest band is still the **R17** pick. |
| 4 | **Device picker** — choose which devices are counted. | |
| 5 | **Device stripes tappable**, with an expanded hit area at phone width. | |
| 6 | **Stripes identify their device** — sticky rotated column header carrying the short name, plus a legend entry. | v1's anonymous 10&nbsp;pt stripe was its weakest part: you could see *that* a device was awake, not which. |
| 7 | **Devices are renameable.** Today they show raw uuids. | Persisted per-device; in the real view this belongs in the settings store, not localStorage. |
| 8 | **Theme, Mode and Range must match the original.** Theme is `light \| dark \| auto` (`stores/settings.ts`, default `auto`, mirrored to localStorage); Mode is `Last duration \| Date range`; Range is the quick-duration row. | Not new features — the surrounding app already has them, and a screen that omits them reads as broken. |

⚠️ **Item 2 has a wrinkle worth knowing before it looks like a bug.** The combined track is
**derived** — computed from device events and stored nowhere — so there is **no combined event to
open in the editor**. The combined detail therefore lists its *source slices* and gives each its own
Edit button; editing the underlying device event recomputes the combined view. The study says this
in the UI rather than leaving it to be discovered.

⚠️ **Device stripes still do not get equal columns at phone width.** **R11** wants the per-device
tracks *present*, not co-equal; three equal columns on a 412&nbsp;pt phone ruins all three. They are
22&nbsp;pt labelled columns at phone width and widen into full labelled columns on tablet and
desktop. If the owner disagrees after using it, this is the knob to turn.

##### Upstream integration — one component, orientation as a prop *(owner: "they would hav eot maintain two pages? therre is no escape from that right?")*

**There is an escape, but only by not building a page.** Upstream would rightly refuse a second
timeline screen — two pages, two bug surfaces, forever. But vertical and horizontal are **not two
pages**: they are one mapping from time to distance with the axis as a variable. Data, colours,
hit-testing, tooltips and shading are identical; only `top/height` becomes `left/width`.

So the upstream PR is *"the existing timeline gains a vertical mode below ~700&nbsp;px"* — desktop
behaviour unchanged, no migration, purely additive, and it fixes a problem upstream has too. That is
a PR that can land, where a new screen is one that sits open.

⚠️ **This reverses a call made earlier the same day.** 3.5's first draft argued for *vertical at
every width, one orientation only, because two orientations will drift*. The fear was right and the
remedy was wrong: **two hand-written renderers** drift; **one renderer with an axis variable** does
not. Each *screen* then picks its own default — the combined view goes vertical at all widths, the
upstream Timeline stays horizontal on desktop.

⚠️ **What is *not* escapable, honestly.** `vis-timeline` is imported in **seven** files —
`views/Timeline.vue`, `views/Bucket.vue`, `views/Report.vue`, `components/SelectableEventView.vue`,
`components/SelectableVisualization.vue`, `util/timelineLabels.ts` and `visualizations/VisTimeline.vue`.
Replacing all of it is a bigger project than this one. So two renderers coexist for a while:

| Screen | Renderer | Maintained by |
|---|---|---|
| Combined view | new component | our fork — upstream has no multi-device merge to hang it on |
| Timeline, narrow | new component | upstream, after the PR |
| Timeline, wide | `vis-timeline` | upstream, unchanged |
| Bucket / Report / pickers | `vis-timeline` | upstream, untouched |

That duplication sits behind **one component boundary rather than in a duplicated page**, and it
shrinks over time instead of growing. The clean contribution split: **the renderer goes upstream**
(general value), **the combined multi-device view stays in the fork** (nothing upstream to merge it
into).

#### 3.5c — Retire the native screen ✅ DONE 2026-09-09
Once 3.5b is verified on hardware, remove `CombinedTimelineView.kt`,
`CombinedTimelineActivity.kt`, `CombinedTimeline.kt`, the `getCombinedTimeline` JNI function and its
`RustInterface` declaration, and point the nav-drawer entry at the web view. **Not before** — see
the warning in 3.5a.

##### What came out

| Removed | Where |
|---|---|
| `views/CombinedTimelineView.kt` (233 lines) | the hand-written canvas renderer |
| `CombinedTimelineActivity.kt` (173 lines) | the day-at-a-time screen and its ‹ › nav |
| `models/CombinedTimeline.kt` (162 lines) | the Kotlin mirror of the JSON the JNI call returned |
| `res/layout/activity_combined_timeline.xml` | its layout |
| the `<activity>` entry in `AndroidManifest.xml` | no Activity left to declare |
| `RustInterface.getCombinedTimeline` | the Kotlin `external fun` |
| `Java_..._getCombinedTimeline` in `aw-server/src/android/mod.rs` | the JNI export |
| ten `combined_*` strings | only `combined_timeline`, the drawer label, survives |

`crate::combined` itself is **untouched** — it still backs `GET /api/0/combined/timeline`, which is
what the Vue view calls. Only the JNI door into it is gone.

`nav_combined` now swaps in a `WebUIFragment` at `#/combined` instead of starting an Activity, so it
moved **into** the drawer's checkable group: it is a screen behind the drawer now, like Activity and
Raw Data, and leaving it checked no longer claims a screen that is not there.

##### Also landed here (owner request, 2026-09-09)

**A Prev/Next block stepper** above the timeline, in `CombinedTimeline.vue`. The owner's point:
*"some of them are small and can't be tapped reliably"*. At a whole-day zoom most blocks are a
couple of pixels across — far under a thumb — and zooming in to hit one loses the context that told
you where to look. The buttons walk the selection along whichever track the selection is in
(Combined when nothing is selected), with an `n / total` counter; ← and → do the same on a desktop,
except while a form control has focus. `ProportionalTimeline` gained `revealRange()`, which centres
the newly selected block in the scroll container — without it the stepper would be useless at
exactly the zoom that makes it necessary.

> **Distinct from [5.5](#55--make-the-aw-webui-timeline-usable-at-phone-width).** 5.5 is upstream
> aw-webui's *existing* **Timeline** screen at phone width. 3.5 is the *new* combined view. They
> both land in aw-webui and will share layout lessons, but they are different screens.

---

## Phase 4 — Manual resolution

### 4.1 — Resolution sheet ⏳ BUILT (2026-09-09) — browser-verified, ⚠️ NOT on device
Tap-to-open, the four outcomes, and the `once` / `always` scope control.
*(R9, R11, R16)*

⚠️ **Build this in aw-webui, not as a native Android sheet.** It opens from a shaded block in the
combined timeline, which [3.5](#35--rebuild-the-combined-timeline-in-aw-webui) is moving into
aw-webui to satisfy **R35** (PC, tablet and phone). A native `BottomSheet` would be stranded on
Android and would have to be written twice. **Do 3.5b first** — a sheet needs a block to open from.
*(3.5b is done. The block to open from is the shaded segment in `CombinedTimeline.vue`; its detail
aside's `div.resolve` placeholder is now a **Resolve…** button that opens the sheet.)*

##### What was built

In **`aw-server-rust/aw-webui`** (branch `beta`, commit `8282a53`):

| File | What it is |
|---|---|
| `src/visualizations/ResolutionSheet.vue` | **New.** The sheet: the competing activities side by side, the four outcomes, the scope control. Bottom-anchored under 576px, a centred dialog above it — one component, not two. |
| `src/util/ulid.ts` | **New.** Decision/tombstone ids. A **ULID, not a UUID**: `id` is the final tiebreak in the `decisions.jsonl` merge ([`05_DATA_MODEL.md`](05_DATA_MODEL.md) §4.2), and sorting by creation time makes that tiebreak stable rather than arbitrary. |
| `src/views/CombinedTimeline.vue` | Opens the sheet, assembles the participant list, handles the emitted decision. |

**The four outcomes and how they are reached.** `foreground` — pick one of the competing
activities. `relabel` — "Something else…", which reveals a text field and refuses to save while it
is empty. `ignore` — "Neither — I was away". `concurrent` — a **checkbox**, "I really was doing
both", not a fifth radio: **R6** says exactly one activity counts, so "both" is a statement about
the *loser* (it lands in `deliberate_background`), not a different winner. Making it a radio would
have implied the totals could split.

**The default pick is the provisional attribution.** That is already what the totals use (**R10**),
so an owner who agrees confirms in one tap; one who disagrees still sees every option.

⚠️ **4.1 stops before writing anything.** The sheet builds a complete decision record — full
**R14** signature included, because **R15** makes today's decisions tomorrow's rules and a signature
not captured now cannot be recovered later — and emits it. Persisting it and recomputing the day is
**4.2**. Until then the sheet says "Not written yet" and shows the record it would append, so the
format is exercised before anything depends on it being right.

`signature.participants[].category` is **null** throughout: `/api/0/combined/timeline` does not
surface a category yet. The field is written anyway, so a later version that does know the category
produces records of the same shape rather than a new one.

##### Verified 2026-09-09 in headless Edge, against a live `aw-server --testing`

Five seeded devices, 16 combined segments, 7 of them contended — driven at **412 × 915** (phone) and
**1280 × 900** (desktop), by script rather than by eye:

| Check | Result |
|---|---|
| Reach a contended block using **only** the stepper, open the sheet | ✅ |
| Sheet scrolls sideways at 412px | ❌ none — `scrollWidth == clientWidth` |
| Smallest option row height (**R34**, one thumb) | **44px**, all of them |
| Both scope buttons the same height | ✅ 44 / 44 |
| Save with `always` + "both" ticked | record has `scope: "always"`, `outcome: "foreground"`, `deliberate_background: ["com.amazon.kindle"]` |
| Save disabled with "Something else…" and an empty label | ✅ |
| Escape closes the sheet | ✅ |
| Console errors | **0** |

**Three defects that run caught**, all fixed in the same commit:

1. **`deviceLabel` fell back to the raw 36-character uuid** — the exact 3.4 defect this view exists
   to fix, and glaring in a sheet whose whole question is *which device*. Now "This device" or
   "Device 823F".
2. **A heartbeat-split event put the same device and app into `background` twice**, so the sheet
   offered two identical radio options — an unanswerable question — and wrote the app into
   `deliberate_background` twice. Participants are deduplicated on `(device, label)`.
3. **Device-track row keys collided** when a device had two events on one timestamp. Vue warned
   about it, and the 3.5c block stepper's key lookup would have landed on the wrong one of the pair.
   The row index is part of the key now.

### 4.1b — Redesign the combined screen for the phone ✅ BUILT + DEVICE-VERIFIED (2026-09-10)

> **Decided by the owner 2026-09-09**, after driving the built app on the S25U. They rejected an
> incremental fix and chose *"design the phone screen properly"* — treat the phone as its own screen
> rather than the 1280px layout squeezed down. *"stop and think this through, the page is starting
> to be a bit missing and hacking a solution is gonna make it worse."* **Do not patch this one
> further. Design it.**

#### The measurement that started it

`412 × 915` in headless Edge, `/#/combined`. A real phone has **less** height than this — the native
action bar takes another ~90px on top.

| Band | Height |
|---|---|
| aw-webui navbar (**a second one**, inside the app's own action bar) | 58px |
| `Combined` + day nav | 34px |
| `Range & devices` fold-out | 36px |
| `View` fold-out | 36px |
| Stat tiles | 57px |
| Minimap | 26px |
| Prev/Next stepper | 31px |
| **Chrome before any data** | **334px** |
| Timeline (fixed `520px`, [`ProportionalTimeline.vue`](../../aw-server-rust/aw-webui/src/visualizations/ProportionalTimeline.vue) `timelineHeight`) | 520px |

#### The actual defect — nested scrolling, not "the panel is too far down"

The page **is** scrollable, by 78px. The owner still could not scroll it, because the timeline is
520 of the ~580 visible pixels: **every drag a thumb makes starts inside the timeline's own scroll
container and moves that instead of the page.** Anything below the timeline is therefore
unreachable — which on device meant the 4.1 **Resolve…** button rendered correctly and could not be
tapped.

⚠️ **The stopgap in `aw-webui@751bf6e` is rejected.** It made `.detail.sheet` a fixed 60vh panel
docked over the timeline. The owner: *"you are gonna make the whole details stay docked? that is
like more than half of the screen."* They are right, and it also fixes neither the nested scrolling
nor the 334px of chrome. **CI run 34405376414 is green and contains it; it was deliberately NOT
installed.** Either fold it into the redesign or revert it — do not build on it.

⚠️ **The headless run passed this screen.** It clicked elements through the DOM instead of scrolling
to them, so it never discovered that a control could not be reached. **Reaching a control is part of
whether the control works** — drive the real app.

#### Owner's decisions for the redesign (2026-09-09)

| Question | Answer |
|---|---|
| **Which of the two top bars survives** | **The aw-webui navbar.** Hide the **native** Android action bar instead. ✅ **Answered 2026-09-10:** Sync Settings and API Authentication move into aw-webui's own **Settings** page as a new group, alongside General / Appearance / Notifications — *"cant we add them to the settings like the real settings that has general appearance notifications and so on"*. See [4.1b-i](#41b-i--give-the-native-settings-a-home-in-awwebuis-settings) below. |
| **What the phone screen is for** | **Resolving overlaps**, **finding one specific stretch**, and **glancing at the day** — all three. The 2026-09-10 answer confirmed the glance stays here; see below. |
| **Resolving on a phone** | **Full support.** *"Phone too."* Big targets, one decision per screen, comfortable to do ten in a row. This gets to shape the layout. |
| **Day nav** | **Stays as it is, next to the title.** *"the day stays as it is next to the title no problem"* |
| **Minimap** | **Stays, and gains dragging.** *"make the minimap strip not just tappable but also scrollable or like draggable"* — drag the viewport marker to scrub the day, not just tap to jump. |
| **Stat tiles** | **Keep the numbers, shrink the furniture.** *"stat tiles are taking extra space they can be neater"* — not removed, made compact. |
| **`Range & devices` + `View` fold-outs** | **Collapse both into a ⚙ button next to TODAY at the top.** *"make the range device and view into a gear button next to the today at the top"* |

✅ **Answered 2026-09-10 — the glance stays, and the Activity remark was about something else.**
The question was whether *"maybe the glance is better represented on the Activity, which should also
be in the map"* meant (1) move the glance off Combined, or (2) give the minimap an Activity band.
**Neither.** The owner: *"if you mean by the glance the strip at the top of the timetable then keep
it. I mean something different, which is the way the Activity now works for a single bucket but
works for multiple devices and combined — so I think a new tab."*

So, for this step:
- **The minimap strip stays exactly as scoped** — kept, made draggable. No Activity band added to it.
- **Combined keeps all three jobs**, glance included, so the stat tiles are *shrunk*, not cut.
- **The Activity remark is not a Combined change at all.** aw-webui's Activity view answers "what did
  I do today" for **one host's buckets**; the owner wants the same view computed **across every
  synced device, over the combined timeline** — as its **own tab**, not folded into Combined.
  Written up as **[4.4](#44--activity-across-devices-a-new-tab)**. It does not block this step.

#### 4.1b-i — Give the native settings a home in aw-webui's Settings

Hiding the native action bar orphans **Sync Settings** and **API Authentication**, which today exist
only in the native drawer. They move into the web UI's own Settings page.

[`Settings.vue`](../../aw-server-rust/aw-webui/src/views/settings/Settings.vue) already renders a
sidebar of groups — `general`, `appearance`, `categorization`, `privacy`, `developer`,
`notifications` — each a list of components, with `/settings/:group` routed in
[`route.js`](../../aw-server-rust/aw-webui/src/route.js). Add a **`device`** group holding sync and
API-authentication settings, shown **only when running inside the Android app** (the desktop build
has no such device, and R35 says the 1280px layout must not change).

Whether the group re-implements those screens in Vue or simply launches the existing native
activities through a JS bridge is an implementation choice for the step — the requirement is that
**from the web UI alone, with the native bar hidden, both are reachable.** This is a prerequisite
for hiding the native action bar, and therefore for acceptance criterion 3.

#### What was built (2026-09-10)

Built from **one rule** rather than patched again: **below 980px the view is exactly as tall as the
viewport, hides its own overflow and locks the body, and everything above the timeline has a fixed
height.** The timeline is then the only scroll container on the screen. Everything else follows.

| Was | Is |
|---|---|
| `Range & devices` + `View` fold-outs, 72px, above the data | A **⚙ sheet** next to the day nav |
| Stat tiles in a box with dividers, 57px | One line, numbers kept, 18px |
| Minimap taps to jump | **Drags to scrub**, with pointer capture so the gesture survives leaving the 26px strip |
| Prev/Next in a 31px band | A **floating pill** over the timeline, 44px buttons, lifted clear of the detail sheet |
| Timeline fixed at 520px | Takes whatever the viewport leaves, measured |
| Detail docked at 60vh (rejected) | A **peek** of ~147px with **Resolve… inside it**; `Details` expands, the next block collapses it |
| Resolution sheet scrolled as one piece | Head and foot pinned, middle scrolls — **Save is never below the fold**; on a phone the evidence list goes, since the options now carry the same swatch, device and duration |
| **334px of chrome** before the first block | **108px** |

Also **4.1b-i** (below): the native action bar is hidden on phones, and the two settings that lived
only behind it have a home in aw-webui's own Settings.

`aw-webui@57fbce9`, `@48f422f`; `aw-android@c43ca12`.

#### Verified in a browser, and what a browser cannot judge

Driven in headless Edge at 412×825 against a live server with **real touch events**, not DOM clicks
— the 4.1 run passed this screen by clicking through the DOM and so never discovered a control it
could not reach. A finger tap selects a block, the pill steps to the next one, and **Resolve… opens
the resolution sheet from inside the peek**. `scrollY` stays 0 through every gesture, including a
full-height drag. At 1280px nothing about the layout changes (**R35**); portrait tablet gets the
compact layout, landscape tablet keeps the desktop one.

⚠️ **Two things that run cannot judge, both for the device:**
1. **Touch *scrolling* does not work in headless Edge at all** — a trivial control page with an
   `overflow: auto` div does not scroll either, so "does a thumb-drag move the timeline" is
   unanswerable there. Wheel scrolling works, which is only evidence the container is scrollable.
2. **How any of it feels in the hand**, which is the thing 4.1b was opened for.

🐛 **Two traps this run walked into, recorded so the next one does not.** `aw-server --webpath`
lost to the server's own compiled-in `rust-embed` copy and silently served a **months-old bundle**;
the run now goes through a small static server instead. And Edge reused a cached bundle **across
runs** even with `Network.setCacheDisabled`, so every verification now starts a **cold browser**.
Both failure modes look exactly like a passing test of code you have already replaced.

#### 4.1b-i — The native settings' new home ✅ BUILT

`Settings ▸ This device`, second in the sidebar, with a row each for **Sync Settings** and **API
Authentication** that opens the app's own screen through `WebUIFragment`'s JavaScript bridge
(`Android.openNativeSettings`). Gated on **the bridge being present**, not on `VUE_APP_ON_ANDROID`:
the same bundle is served by a desktop aw-server, and a build flag says how the bundle was compiled,
not whether an app is on the other side of it. On a desktop the group is absent, not empty.

The native action bar is then hidden **on phones only** (`smallestScreenWidthDp < 600`, read from
the live configuration so a foldable is asked again), and the drawer is **locked shut** rather than
left swipeable with no advertised way back to it. Everything else the drawer held — Home, Activity,
Raw Data, Combined, Settings — is in aw-webui's navbar already.

#### On the S25U (2026-09-10) — what hardware confirmed, and what it found

Driven over adb on `R3CY901YCEB`. One top bar. A thumb-drag on the timeline scrolls it and the page
stays put. The minimap jumps. The peek opens with its action inside it, the resolution sheet fits
one screen with **Save** visible, and `Settings ▸ This device ▸ Sync Settings` launches the real
`SyncSettingsActivity`. The sheet handle's three states — pull up to expand, release to snap, pull
down to dismiss — all work in the hand.

Hardware and the owner then found **five things a 412px browser did not**:

| Found | Why the browser missed it |
|---|---|
| The stat strip truncated every label — `combin…`, `unresolv…` | Test data had small numbers; `13h 32m` is what overflows |
| Both native settings screens drew their first row under the status-bar clock | Pre-existing, but 4.1b removed the drawer that used to lead there. `fitsSystemWindows` |
| The Prev/Next pill "looks bad" — a dark grouped pair inside a white pill, counter hanging off the end | Measurable as *present and tappable*, which is what was measured |
| The sheet handle looked draggable and was tap-only | A handle that does not drag is a picture of a handle |
| `Resolve…` read as a truncated word, not as "opens a dialog" | The convention is invisible to a measurement, and to most people |

The last three are the same lesson: **a control can pass every measurement and still be wrong**, and
only the owner's eye catches that. Fixed in `aw-webui@4a758fa` and the run below.

#### The four the owner found next, on the built screen (`aw-webui@27c6f1c`)

Every one of them was the same mistake wearing different clothes: **a decision made for the phone
alone, when it was the better answer everywhere.**

1. **"I really was doing both" is now one checkbox per losing activity.** It was already a checkbox
   rather than a fifth radio, and that part is right — **R6** says exactly one activity counts, so
   this is a note about the ones that lost, not a second winner. But a *single* tick has no answer
   when three devices are in play: it silently marked **every** loser deliberate. On a real overlap
   of Syncthing-Fork / YouTube / Syncthing-Fork, ticking YouTube now writes
   `deliberate_background: ["YouTube"]` and nothing else — checked against the record the sheet
   builds. Each row carries its device, because two devices running the same app otherwise produce
   two rows that read identically (which they did, in the 1280px screenshot).
2. **The ⚙ is the control model at every width.** The two fold-outs stayed in the flow on wide
   screens because there was room for them — but room is not a reason to spend 72px above the data
   on every visit for a control used once a session, and having the phone answer the question one
   way and the desktop another means two things to learn instead of one. The desktop now opens
   straight on the data, with the same ⚙ beside the day nav.
3. **No `Resolve…` left anywhere.** The wide layout still had one.
4. **Carry-over is the default now.** Anything the phone work improved that is not *about* being
   small — the pinned head and foot on the resolution sheet, the swatch/device/duration on each
   option, the drag-to-scrub minimap — already applies at every width, and stays that way.
5. **And a fifth, found by plugging the tablet in.** Asked whether the tablet had been left out, it
   turned out it had, in the most visible way possible: the **SM-X520 was still stacking two title
   bars** — the native one and the web UI's navbar directly beneath it — which is the exact defect
   4.1b was opened to fix, left in place on the device that shows it most plainly. See
   [4.1b-ii](#41b-ii--the-native-action-bar-goes-at-every-width).

#### 4.1b-ii — The native action bar goes at every width ✅ BUILT

4.1b hid it on phones only (`smallestScreenWidthDp < 600`), out of caution: the drawer was the sole
route to three Android-only screens, and orphaning a setting is exactly what **R30** records. That
caution is now spent, and keeping it had a cost the tablet was paying every time it was opened.

What was behind the hamburger, and where it is now:

| Drawer item | Where it lives |
|---|---|
| Home, Activity, Raw Data, Combined, Settings | The web UI's own navbar, already |
| Sync Settings, API Authentication | `Settings ▸ This device` (4.1b-i) |
| **Open in browser** | `Settings ▸ This device` — **added here**, and it was the real hole |
| Report bugs | Never implemented; it showed a snackbar saying so. The web UI's footer has a real link |

**Open in browser was already orphaned on the phone by 4.1b and nobody noticed** — which is
precisely how R30 happened the first time, and the reason this table exists rather than a sentence
claiming the drawer was empty. It goes through the same `Android.openNativeSettings` bridge under
the name `browser`, handled by `MainActivity` because it owns the URL and the API key that
authenticates it. `WebUIFragmentTest` now lists **every** name the web UI can send: a name the
bridge forwards but `MainActivity` ignores is a dead button, and that is the failure mode to guard.

A tablet is not a phone. That is not a reason to keep a second title bar whose only remaining
content duplicates the navbar underneath it.

#### What the redesign must fix, restated as acceptance criteria

1. **One scroller, or none.** A thumb-drag anywhere on the screen must move something useful. No
   nested scroll container competing with the page. — ✅ *on hardware: the timeline scrolls under a
   thumb and the page does not move.*
2. **Every control reachable by thumb**, verified by *scrolling and tapping on hardware*, not by
   DOM-clicking in a headless browser. — ✅ *walked on the S25U.*
3. **One top bar**, not two. — ✅ *native bar hidden on phones; 4.1b-i gave the orphans a home.*
4. **Chrome well under 334px** before the first block. — ✅ **108px.**
5. **The resolution sheet is comfortable for ten decisions in a row** — that is now a primary job,
   not an occasional one. — ✅ *Save always visible, 44px targets, one screen. The "also deliberate"
   ticks are per-activity now, which is what makes a three-way overlap answerable at all.*
6. Still **R35**: whatever this becomes must not break the tablet or the 1280px desktop layout. —
   ✅ *1280px re-measured after the ⚙ change: no horizontal scroll, layout intact, and it gained the
   phone's chrome saving rather than losing anything.*

#### Not blocked by this

4.2 and 4.3 are data-layer work in Rust and Kotlin and do not depend on the phone layout, and
**4.4** is a separate screen. Nothing here blocks anything else. **Next: 4.2.**

### 4.2 — Persist + apply decisions ⬜
Write to `decisions.jsonl`; apply exact matches, then signature rules; mark `auto_resolved`.
**Check:** resolve on A → after sync, B shows the same resolution and no longer asks. *(R26)*

### 4.3 — Undo ⬜
Tombstones; segment returns to shaded. *(R12)*

### 4.4 — Activity, across devices (a new tab) ⬜ ← *owner-requested 2026-09-10*

> *"the way the Activity now works for a single bucket but works for multiple devices and combined —
> so I think a new tab."*

aw-webui's **Activity** view ([`views/activity/Activity.vue`](../../aw-server-rust/aw-webui/src/views/activity/Activity.vue),
`ActivityView.vue`) answers *"what did I do today"* — top apps, categories, the day's totals — for
**one host's buckets**, chosen by a host picker. Everything Phase 3 built now makes the same
question answerable across **all** synced devices at once: `aw-combined` already segments, classifies
and attributes the day (**R6/R17**), so each stretch of time has exactly one device and app that
counts toward totals. Feeding *those* attributed slices into Activity's existing summaries gives a
genuinely multi-device day, with no double-counting when two devices were awake at once.

- **A new tab, not a mode of Combined.** Combined stays the *resolve and find* screen (4.1b);
  this is the *what did I do* screen. Two jobs, two tabs.
- **Reuse the Activity components** — the same top-apps / category / total summaries, fed from the
  combined attribution instead of one host's bucket queries. Do not fork the view.
- **Contested time needs an honest treatment.** Segments still awaiting a decision are attributed
  *provisionally*. Decide whether they count, count as their provisional pick, or are shown
  separately — and make it visible rather than silent.

**Check:** on a day with events from two devices, the tab's total active time is not the sum of the
two devices' overlapping totals, and resolving an overlap in Combined changes the numbers here.

> **End of 4.1–4.3 = the product the owner originally asked for.** 4.4 was added 2026-09-10 and is
> the first thing beyond it; everything after that is convenience.

---

## Phase 5 — Make the UI usable on a phone

Layer 2 from [`02_ARCHITECTURE.md` §7.2](02_ARCHITECTURE.md) — the part 1.6 cannot fix.
Target is **functional, not beautiful** (**R34**): nothing cut off, nothing unreachable, every
control tappable in portrait with one thumb.

> ## ✂️ Scope cut 2026-09-02 — this phase shrinks to "reachable and not broken"
> The owner, on seeing aw-webui on the phone: *"the UI is not usable on the phone at all … I do
> think the intended way to use it is with a browser."*
>
> **Do:** 5.3 (kill horizontal scroll) and what 1.7 already landed. **Do not:** a general pass to
> make aw-webui's desktop screens pleasant on a phone. That effort goes to **3.4** instead, which
> is native, phone-first, and the screen actually opened every day (Q4).
>
> ↩️ **Partly reversed 2026-09-09, by the owner, for the Timeline only:** *"we eventually want the
> timeline to be better on small screens like the phone — on the tablet it is good."* The cut still
> holds for aw-webui in general; the **Timeline** is carved back out as [5.5](#55--make-the-aw-webui-timeline-usable-at-phone-width),
> because it is the one aw-webui screen actually read on the phone. Still "functional, not
> beautiful" (**R34**) — fit the controls, give the tracks the width, do not redesign it.
>
> ⚠️ **"Just use a browser" is not free.** The embedded server binds `127.0.0.1`, so a desktop
> browser cannot reach a phone's data without changing what it listens on — a security decision,
> not a convenience one. Do not assume this route exists until someone has decided that.
>
> aw-webui stays the deep-analysis tool at a desktop. That is a fair division of labour, not a
> retreat.

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

Tablet is fine throughout, so this is **narrow-width only**. ⚠️ Still owed: the *specific* CSS rule
per element. Pin the rest down with WebView remote debugging (`chrome://inspect`), which works
because `WebView.setWebContentsDebuggingEnabled(true)` is already set in testing builds.

**Timeline, captured on the S25U 2026-09-09** (screenshot taken while verifying 3.4) — the element
list 5.1 was missing, at least for this screen:

| Element | What happens at phone width |
|---|---|
| `Mode` segmented control | Runs off the right edge; **"Date range" reads "Date rang"** and is not fully tappable |
| `Range` segmented control (`¼h ½h 1h 2h 3h 4h 6h 12h 24h`) | Nine buttons on one row; **"24h" is cut off** |
| `643 Events shown:` box | Clipped at the right edge, value not visible |
| Bucket table label column | Takes **~55% of the width**, squeezing every track into the right 45% |
| Bucket row labels | `android-synced-from-jude` truncated — note this is the *layout* truncating, on top of the separate `_` truncation bug in [1.10](#110--timeline-truncates-every-peers-name-at-the-first-_) |

The owner's summary, unprompted: **"on the tablet it is good"** — so this is purely a width problem,
which is what [5.2](#52--decide-q8--resolved-2026-09-02--css-not-native) already concluded.
### 5.2 — Decide Q8 ✅ RESOLVED 2026-09-02 — CSS, not native
Patch `aw-webui`'s CSS (**B**), or inject a mobile stylesheet from the WebView (**A**).

**Decided:** the phone audit (5.1) found only a few overflowing control groups while the tablet is
fine throughout, so **C (native screens) is ruled out** — it would rewrite screens that already work
everywhere except a handful of rows. Prefer **B** where the fix is a genuine responsive improvement
worth carrying upstream; fall back to **A** for anything too fork-specific to upstream.

⚠️ **Refined 2026-09-09, not reversed.** The **Timeline** turned out to need more than CSS — it is
built on `vis-timeline`, which has no vertical mode, and no stylesheet makes a 24-hour horizontal
axis readable on a phone. That is a **new Vue component**, still web and still upstreamable, so the
verdict "CSS, not native" survives: the answer is never an Android screen. But read "CSS" as "in
aw-webui" rather than "stylesheet only". See [5.5](#55--make-the-aw-webui-timeline-usable-at-phone-width).

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

### 5.5 — Make the aw-webui **Timeline** usable at phone width ⬜ ← *owner-requested 2026-09-09*
The Timeline is the screen the owner actually reads, and it is the worst offender at phone width.
**On the tablet it is good** — this is narrow-width only, so it is a responsive-layout job, not a
redesign, and per [5.2](#52--decide-q8--resolved-2026-09-02--css-not-native) it is **CSS (B), not a
native rewrite**.

⚠️ **Corrected 2026-09-09: CSS alone cannot finish this one.** 5.2's "CSS, not native" still holds —
the fix is not an Android rewrite — but the element list below was written before anyone checked
what the Timeline is *built on*. It is **`vis-timeline`**, which has **no vertical mode**: its time
axis is horizontal, full stop. CSS can stop things being clipped, and should; it cannot make a
24-hour horizontal axis readable at **14&nbsp;pt per hour**, which is what a phone gives (see the
arithmetic in [3.5b](#35b--the-vue-view)).

So this step splits, and its larger half is now downstream of Phase 3:

- **5.5a — stop the clipping** *(CSS, do any time)*: the `Mode`/`Range` controls wrap or scroll, the
  events box is not cut off, the label column stops eating ~55% of the width. This is worth doing on
  its own and does not wait for anything.
- **5.5b — give it a vertical mode** *(waits on [3.5b](#35b--the-vue-view))*: adopt the renderer
  built for the combined view and use it for the Timeline below ~700&nbsp;px. **This is the actual
  fix**, and it costs almost nothing extra once 3.5b exists — which is the argument for building
  3.5b's renderer as a general component rather than something combined-view-specific.

**Do not** attempt 5.5b before 3.5b, and do not write a second vertical renderer for it.

This is the one screen carved back out of this phase's 2026-09-02 scope cut, at the owner's
request on 2026-09-09 — see the note at the top of Phase 5. Target stays **R34: functional, not
beautiful.**

What "better" means here, beyond [5.3](#53--kill-horizontal-page-scroll)'s "does not scroll
sideways" — a Timeline can fit the viewport and still be unreadable:

- **The two segmented controls (`Mode`, `Range`) must fit or wrap.** Nine range buttons on one row
  do not fit a phone; wrap them, scroll them in their own container, or collapse to a dropdown.
- **Give the tracks the width.** The label column eats ~55% of the screen; labels belong above
  each track, or in a much narrower column, so the actual data gets the space.
- **Track rows tall enough to read**, and bucket names not truncated by the layout.

**Check:** on the S25U in portrait, every `Mode` and `Range` option is fully visible and tappable,
no element is clipped at the right edge, and each bucket track is at least as wide as its label
column. Compare against the 2026-09-09 screenshot in [5.1](#51--audit-what-actually-breaks--mostly-done-2026-09-02).

⚠️ **Distinct from the native combined-timeline screen** built in
[3.4](#34--combined-view-with-shading), which has its own design debt recorded there. This item is
about **upstream aw-webui's Timeline**, rendered in the WebView.

> **New screens are exempt from this phase — they must be born mobile-first (R33).** The combined
> timeline (Phase 3.4) and resolution sheet (Phase 4.1) are designed at phone width from the start,
> so they never join the backlog this phase exists to clear. ⚠️ **3.4 did not honour this** — its
> first pass has raw UUIDs colliding with the duration text and no legend; the design pass owed
> there is tracked in 3.4's own entry, not here.

---

## Phase 6 — Later

- **Upstream what this fork fixed.** Not charity — several are bugs upstream has *already received
  reports about* and would otherwise fix twice:
  | Ours | Upstream evidence | Status |
  |---|---|---|
  | `title` on window events (1.8) | aw-webui `bf0fc84` regression | **reported: [aw-webui#959]** |
  | Timeline name truncation (1.10) | aw-webui `dc02ac8` `shortenBucketLabel` | ⬜ found 2026-09-03, not reported |
  | Sync Now + inline result (1.7) | **#247**: *"sync has no feedback, no 'last date synced' or failed/success indicator"* | ⚠️ **overlaps [PR #251]** |
  | Failure reporting (1.9) | **[PR #251]** fixes the same bug upstream | ⚠️ **overlaps [PR #251]** |
  | Restored toolbar (1.7) | **#247**: *"I only found out with Qwen that there is a left-swipe menu"*; **#218** closed as unreachable | not sent |
  | Bidirectional SAF mirror (1.4) | **#247**: *"sync seems to not work. No change in the chosen folder"* | not sent |
  | Push/pull depth, pull-every-db (1.2, 1.3) | no upstream report — Android-only paths | not sent |

- ⚠️ **Read [PR #251] before sending anything from 1.7 or 1.9.** It is **open, not merged**, and it
  touches the same four files: `AWPreferences.kt`, `SyncInterface.kt`, `SyncSettingsActivity.kt`,
  `activity_sync_settings.xml`. It persists the last sync's time and outcome and shows
  `Last sync: never / succeeded at … / failed at …`. Ours adds an on-demand **trigger** and honest
  failure propagation. The two are complementary, not duplicates — but the honest pitch is *"adds a
  manual trigger on top of #251's status display"*, and a textual conflict in all four files is
  certain. Rebase onto it rather than proposing a parallel design.

> **Decision 2026-09-03 — keep the 1.8 `title` fix; do not withdraw it when #959 is fixed.**
> The question is worth answering once, because it looks like duplicated effort. It is not:
> - **#959 is an open issue, not a merged fix.** Nobody is assigned. Withdrawing ours now would
>   take the Activity view straight back to `Time active: 0s`.
> - **They repair different layers.** #959 fixes aw-webui's *query*. 1.8 makes Android events carry
>   the `title` field every other watcher already emits. An Android event without a `title` is the
>   odd one out regardless of what any query does with it.
> - **It survives the next change.** The regression happened because a query was retuned for a
>   different client sharing the Android branch. That can happen again; a real field cannot be
>   re-broken by a query edit.
> - **The costs are small and known:** a duplicated string per event (`title` == `app`, the screen
>   name stays in `classname`), and one more diff to carry against upstream.
>
> **Revisit only if** upstream fixes #959 by giving Android a *meaningful* title — the activity or
> screen name rather than the app label. Then ours would disagree with theirs and should yield.
> Nothing about the fix is retroactive either way: pre-fix events have no `title` recorded, so only
> an upstream query fix can ever surface them.

[aw-webui#959]: https://github.com/ActivityWatch/aw-webui/issues/959
[PR #251]: https://github.com/ActivityWatch/aw-android/pull/251
- An aw-webui combined view (Q4's second half) is the other natural contribution.
- Rules engine proper — generalise accumulated signatures (**R15**; the data is already there).
- Device role priors (screen-off is never foreground).
- Desktop client (**R2**).
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

### 2026-09-09 (latest) — sync outage: a blind SAF listing became 128 duplicate files

**Symptom.** "Sync Now" reported failure on the phone, and the directory picker would not let a
folder be chosen. Reported right after an APK install, which is what it looked like — and was not.

**What actually happened, in order:**

1. Android's **MediaProvider** went stale and began reporting
   `/storage/emulated/0/ActivityWatch-sync` as *hidden*. `adb` could list the folder; only the SAF
   layer was blind. The tell in logcat was `Queried directory "primary:ActivityWatch-sync" is
   hidden` alongside `java.nio.file.NoSuchFileException: /storage/emulated` from
   `com.android.externalstorage`.
2. `queryChildDocuments` therefore returned **empty**, so [`SharedFolder`]'s `findFile()` returned
   null for everything.
3. Null was read as *"absent"*, so each sync created the document again. **SAF never fails on a
   name collision** — it silently uniquifies. So every sync left another `VERSION (n)`,
   `devices (n)`, `jude_s_s25_ultra (n)`, `jude_s_tab_s10_fe (n)`.
4. Syncthing replicated all of it to the tablet.
5. At copy **32** SAF's uniquifier gave up — `FileNotFoundException: Failed to create unique file`
   → `Could not write VERSION` → the visible "sync failed".

**Not caused by the install.** Every duplicate is timestamped 11:12–18:26; the APK went on at
21:14. Worth stating plainly because the timing was genuinely misleading.

**Recovery.** `am force-stop com.google.android.providers.media.module` (plus
`com.android.externalstorage`) on both devices. Sync went green immediately — and, decisively, the
duplicate count *stopped rising*, which is what confirmed the diagnosis. A reboot would do the same.

**Cleanup.** 128 stray entries (32 each of four names) removed from the **phone only**; Syncthing
carried the deletions to the tablet, and `.stversions` archived them. Verified before deleting:
every `VERSION (n)` held the same `1`, every stray `meta.json` was byte-identical to the real one
but for an older `last_seen`, and the real `test.db` files were **newer and larger** than any stray
(3,190,784 B at 21:46 vs 3,174,400 B at 17:44). The bare `VERSION`, `devices/`,
`jude_s_s25_ultra/` and `jude_s_tab_s10_fe/` were never in the delete set.

**The fix.** [`SharedFolder`] now routes every create through `insistOnName`: *the name we get back
must be the name we asked for*. If SAF answers `VERSION (1)` to a request for `VERSION`, the folder
is not listing correctly — so refuse, delete the stray, and fail loudly. This catches the failure on
the **first** duplicate rather than the thirty-third, and it also subsumes the earlier
2026-09-09 `text/plain` → `VERSION.txt` bug (see [2.1 verified](#2026-09-09--21-verified-on-device-after-catching-a-saf-extension-bug)), which was the same
"findFile can't see what we wrote" shape with a different cause.

⚠️ **The underlying MediaProvider staleness is an OS fault we cannot prevent** — the guard stops it
turning into 128 files and a broken sync, but a stale provider will still make a sync fail until the
provider is restarted or the device rebooted. Left as a known operational note, not a code task.

One leftover, deliberately untouched: a stale `.syncthing.VERSION.tmp` from 11:12. It is Syncthing's
own temp file and Syncthing manages its lifecycle.

### 2026-09-09 (latest) — 3.4: the combined view, in code
`aw-combined` is wired to a screen. A **Combined timeline** item in the nav drawer opens
`CombinedTimelineActivity`, which asks Rust for one day and draws the combined track above one row
per device, with unresolved contention striped (**R8**).

- **`aw-server/src/combined.rs`** is the new datastore adapter: it reads the range, splits buckets
  into activity (`currentwindow`) and idle (`afkstatus`, filtered to `status == "afk"`), runs the
  pipeline plus `coalesce`, and shapes the JSON. It sits outside `android/` on purpose so a desktop
  `cargo check` compiles it — `android/` is `cfg(target_os = "android")` and is never host-checked,
  which is exactly where a mistake would hide until CI.
- **`getCombinedTimeline(start, end, hostnameToUuidJson)`** is the new JNI entry point; the own
  device uuid is read Rust-side from `device_id` so the halves cannot disagree.
- **Kotlin:** `models/CombinedTimeline.kt` (Android-free parser), `views/CombinedTimelineView.kt`
  (the drawing), `CombinedTimelineActivity` (day picker, summary, tap detail).
- **The summary line shows the R6 gap** — combined total vs the sum of the devices' totals.
- Judgment calls: `currentwindow` only (a web bucket would contend with its own window); per-device
  rows from raw events (**R11**) via the now-public `resolve_device` (**R19**); empty hostname→uuid
  map, so pre-3.1 history shows under its hostname rather than being folded into us; stripes rather
  than a tint for shading.
- `check-local.sh` gained `cargo check -p aw-server --lib`. All local checks green.
- `aw-server-rust@beta` → `e2e7db3`; submodule pointer moved.
- ✅ **Verified on both devices** (CI [34366963972] / `9f6f855`). Phone: *"7h 50m combined, from
  9h 29m across 2 device(s), 16 shaded"*; tablet: *"8h 15m from 9h 53m, 2 devices, 15 shaded"*.
  **R6 holds on both.** Two device-only bugs were found and fixed on the way: the Activity crashed
  on every launch (`LocalDate.now()` in a property initialiser, before `AndroidThreeTen.init`), and
  the tablet contended with **itself** because origin was resolved per event rather than per bucket
  — it appeared as `jude_s_tab_s10_fe` *and* `7b54cfe9…`, which is its own `device_id`. After the
  fix it is one row of exactly 1h 39m, the sum of the two it was split into.
- The two devices report different totals, legitimately: each holds only as much of its peer's
  history as its last sync brought over. **R18 is about identical input**, not about two devices
  agreeing mid-sync.
- ⚠️ **UI is known bad and the design pass is deferred** at the owner's instruction. Worst first:
  raw UUIDs colliding with the duration text at phone width; tap detail that names apps but not
  devices (so real cross-device contention reads as *"Syncthing-Fork — also running:
  Syncthing-Fork"*); no legend. Full list in 3.4.
- **The owner then raised the UI bar, which changed the architecture** (2026-09-09): top-of-the-line
  rather than adjustments, look like the **Activity view**, and **work on PC, tablet and phone**.
  Written up as **R35**/**R36**. A native Android `View` cannot run on a PC, so the combined
  timeline moves to **aw-webui** — see [3.5](#35--rebuild-the-combined-timeline-in-aw-webui).
- **3.5a done:** `GET /api/0/combined/timeline` now serves the pipeline over HTTP, wrapping the same
  `aw-server/src/combined.rs` the JNI path uses so the two cannot drift. Verified against a running
  server, not just compiled: two devices overlapping 30 min gave `combined_seconds` **5400** against
  device totals of **7200** (**R6**), with the overlap `contended`/`unresolved` (**R8**), and all
  three bad-input cases returned `400` naming the parameter.
- **3.5b built and verified in a browser** (2026-09-09), not on device. New `CombinedTimeline.vue`
  plus a generic `ProportionalTimeline.vue` whose orientation is a prop — that second one is what
  5.5b reuses and what could go upstream. Run against a live server with three seeded devices: **R6
  held (368 min combined vs 608 min device sum)** and a **4-slice** contended segment rendered
  legibly, which was the case flagged as unverified in the layout study. Three device-class bugs
  were caught by actually looking: white-on-dark gutter headers, duration text leaking into 22px
  stripes, and a navbar brand collision my new nav entry caused at 1180px.
- ✅ **Fork created (2026-09-09).** `Judemasic/aw-webui` now exists; `beta` (`cb3b0c3`) is pushed.
  `Judemasic/aw-server-rust@beta` (`57a29f6`) repoints `.gitmodules` at the fork and pins `cb3b0c3`;
  `aw-android@beta` bumps its `aw-server-rust` submodule to match. CI reads the fork URL from each
  `.gitmodules`, so a build can now contain this screen. In the webui checkout `origin` is the fork
  and `upstream` is `ActivityWatch/aw-webui`.
- ⚠️ **True phone width (412px) is still untested**: headless Edge will not go below a 510px
  viewport. The vertical layout is exercised at 510; the narrowest case waits for hardware.
- ✅ **3.5b verified on device** (owner, 2026-09-09): *"the zoom works"*. That was the last thing
  holding 3.5c.
- ✅ **3.5c done (2026-09-09).** The native Kotlin screen, its layout, its model, its `<activity>`
  entry, the `getCombinedTimeline` JNI export and the Kotlin `external fun` are all gone; ten
  now-dead `combined_*` strings went with them. `nav_combined` opens `#/combined` in a
  `WebUIFragment`, so the two combined views have collapsed to one. `crate::combined` stays — the
  HTTP endpoint the Vue view calls is unchanged.
- ➕ **Owner request, landed with 3.5c:** Prev/Next buttons to step between timeline blocks, because
  *"some of them are small and can't be tapped reliably"*. Arrow keys too on a desktop. The renderer
  gained `revealRange()` so the stepped-to block is scrolled into view.
- ⏳ **4.1 built (2026-09-09), browser-verified, not on device.** `ResolutionSheet.vue` plus
  `util/ulid.ts`. Driven by script in headless Edge at 412px and 1280px against a live server: no
  horizontal scroll, every option row 44px, both scope buttons equal height, zero console errors,
  and the emitted record carries the full R14 signature. It **does not write yet** — that is 4.2.
- 🐛 That run caught three real defects, fixed in the same commit: a raw-uuid device label (the 3.4
  defect, back again), duplicate participants from a heartbeat-split background event, and colliding
  row keys that the new stepper would have resolved to the wrong block.
- 📱 **Installed on both devices and driven over adb.** 3.5c confirmed on hardware: the drawer entry
  stays in `MainActivity` and swaps in the web view, and the stepper works — eight taps moved the
  counter to **8 / 499**, and tapping a block jumped it to **148 / 499**, so taps and buttons share
  one selection.
- 🐛 **That run found a defect the headless run had passed:** on a phone the detail panel, and the
  4.1 **Resolve…** button in it, could not be reached. The timeline is 520 of ~580 visible pixels
  and is its own scroll container, so every thumb-drag scrolls it rather than the page. The headless
  run missed it by clicking through the DOM instead of scrolling.
- ✋ **The owner stopped the incremental fix** and chose a proper phone redesign — see
  [4.1b](#41b--redesign-the-combined-screen-for-the-phone). The docked-sheet stopgap in
  `aw-webui@751bf6e` is **rejected**; CI 34405376414 is green with it and was **not installed**.
- ✅ **Both of 4.1b's open questions answered by the owner 2026-09-10.** The minimap strip stays
  (glance included, stat tiles shrunk not cut); the native Sync Settings and API Authentication get
  a home as a new **group inside aw-webui's own Settings page**, which unblocks hiding the native
  action bar. The Activity remark turned out not to be about Combined at all — it asks for the
  Activity view computed across every device, as its own tab: now
  **[4.4](#44--activity-across-devices-a-new-tab)**.
- ✅ **4.1b and 4.1b-i built and verified on the S25U 2026-09-10.** One scroller, the fold-outs
  behind a ⚙, a peek-sized detail sheet with the resolve action **inside** it, a scrubbing minimap,
  a draggable sheet handle, and 334px of chrome down to **108px**. The native action bar is gone on
  phones, and Sync Settings and API Authentication live in `Settings ▸ This device`.
- ⚠️ **Headless cannot touch-scroll** — proven against a trivial control page — so the thumb-drag
  question was only ever answerable on hardware, where it passed.
- 🐛 That run also caught two ways a verification can quietly check the *old* code: `--webpath`
  losing to the server's embedded copy, and Edge reusing a cached bundle across runs.
- 👁️ **Five defects survived a passing browser run and were caught by eye** — a truncating stat
  strip, two screens under the status bar, an ugly stepper, a handle that only looked draggable,
  and an ellipsis that read as a cut-off word. Then four more from the owner on the built screen,
  all the same shape: **a phone decision that was the better answer everywhere.** The ⚙ now holds
  the fold-outs on a desktop too, and "I was doing both" became **one checkbox per other activity**
  — the single tick had no answer for a three-way overlap and marked every loser deliberate.
  `aw-webui@27c6f1c`, `aw-android@23478c8`.
- 📱 **Then the tablet was plugged in, and it was still showing two title bars.** 4.1b hid the
  native one on phones only, out of caution about orphaning the drawer's contents; that caution was
  spent once 4.1b-i rehoused them, and **Open in browser turned out to be already orphaned on the
  phone** — R30 repeating, unnoticed. It joins `Settings ▸ This device`, and the action bar is now
  hidden at every width. `aw-webui@c8c8a7a`, `aw-android@1755401`.
- Next: **4.2 — Persist + apply decisions**.

### 2026-09-09 (later) — 3.3: provisional attribution + coalesce
Steps ⑤ and ⑥ of `04` §2. `aw-combined::attribute` now runs inside `compute_segments` right after
classify and picks, for every segment, the single slice that counts toward day totals (**R6/R17**);
`aw-combined::coalesce` is a **separate opt-in** function that merges heartbeat-split slivers for
the view.

- **The catch this step existed to fix:** R17 rule 1 ("longest total duration in the segment wins")
  was **dead code** against the old types — every slice covers its segment exactly, so every
  candidate was the same length and every pick fell through to "lowest device UUID". `ActiveSlice`
  now carries `source_start` / `source_end` — the span of the **originating** activity interval,
  **post-idle** — so the phone's hour-long YouTube session actually beats the tablet's
  fifteen-minute Kindle dip.
- **Total order:** longest source span → lowest `device` → lowest `bucket_id` → lowest canonical
  `data` string. The last two keep the result independent of input order (**R18**).
- **`unresolved` = `Contended`**; a short-contention segment demoted to `Settled` is not shaded but
  still gets a provisional pick — which **closes the open point 3.2 left**: its time goes to its
  longest-running activity, by placement not by decision.
- **`coalesce` keys on the foreground `(device, data)` pair**, not the whole `active` vec, because
  a heartbeat-split session's slices differ by source span and would otherwise never merge. It is
  lossy (merged `active` is the union), so `compute_segments` never calls it and Phase 4 keeps the
  atomic segments.
- **`tests/invariants.rs`** asserts **R6** directly: totals equal the union measure of the
  post-idle intervals (hand-computed, not the device-duration sum), one foreground per segment,
  non-overlapping, conserved by `coalesce`. 42 `aw-combined` tests green; `aw-sync` still green;
  workspace check clean; `check-local.sh rust` passes.
- `aw-server-rust@beta` → `a39f52e`; submodule pointer moved. Carries only the unused crate, so no
  CI build and no APK — 1.11's and 3.1's hardware verification stand. Nothing to test on a device.
- Next: **3.4** — wire `aw-combined` to a native phone-first view over JNI, shade `unresolved`
  segments. First Phase 3 device test; needs a CI build and an APK.

### 2026-09-09 (night) — 3.2: the segmentation pipeline, in a crate of its own
The first half of the combined-timeline pipeline (`04` §2 steps ①②③) is now real Rust, in a new
`aw-server-rust` workspace crate **`aw-combined`**. One pure function,
`compute_segments(PipelineInput) -> Vec<Segment>`, turns every device's post-sync activity and idle
events into non-overlapping atomic segments labelled `Settled` or `Contended`. No datastore access,
no file I/O, no clock — the caller passes events in, so a future desktop client and an aw-webui view
reuse it unchanged (**R2**, **Q4**).

- **①** resolves each event's origin device (3.1 tag → `-synced-from-<peer>` suffix, map-hit gives
  the UUID and a **map-miss keeps the string verbatim** for the 1.5 `-synced-from-<uuid>` case →
  local UUID), subtracts idle, and drops zero-width heartbeats. **②** is a boundary sweep with no
  merging — segments are atomic. **③** marks `Contended` on ≥2 distinct devices, then absorbs
  contended *runs* shorter than 60 s (D15) into `Settled` with an `absorbed_short_contention` flag.
- Two judgment calls, both recorded in the 3.2 Result: the minimum-duration threshold is applied to
  a whole contiguous **run** of contended segments (not each atomic segment, which would shred a
  long contention below the threshold and erase it); and short contention is **demoted-and-flagged**
  rather than merged into a neighbour (undefined when neighbours differ or are absent). Which
  activity a demoted segment's time is credited to is an open point handed to 3.3.
- `EVENT_ORIGIN_KEY` moved from `aw-sync` to `aw-models` so `aw-combined` can share the literal
  without depending on `aw-sync`; `aw-sync`'s re-export is unchanged and its 28 tests still pass.
- 29 tests (`cargo test -p aw-combined`): 14 golden + 6 unit + 9 adversarial, covering the `04` §6
  worked example,
  a three-device case (**R1**), all origin-resolution branches, idle subtraction and splitting, the
  run-threshold regression guard, exactly-60 s, zero-width drop, atomic-boundary preservation, and
  an input-shuffle determinism check (**R18**), plus 9 adversarial tests for what ends a contended
  run, per-device idle, and corrupt rows. `check-local.sh rust` gained
  `cargo check -p aw-combined --lib`.
- ⚠️ The boundary sweep is **O(n²)** — measured 3k events 18 ms, 15k 253 ms, 30k 870 ms (release,
  desktop). Comfortable for the day view 3.4 builds; a week or month range would need a sweep line.
  Recorded, not fixed — nothing asks for a multi-day range yet.
- **Nothing to test on a device.** Pure Rust over fixed inputs; nothing calls the crate yet. The
  `aw-server-rust` submodule pointer moves forward carrying only an unused crate — no CI build or
  APK needed, and 1.11's / 3.1's hardware verification stays valid.

### 2026-09-09 (night, later) — 3.1 verified on both devices, driven over adb
The 3.1 device test from the entry below, run end to end without the owner touching a screen. The
CI build [34343835497] (`feb127e`) APK was installed on both the phone (`SM-S938B`) and the tablet
(`SM-X520`) with `adb install -r`; its `libaw_sync.so` was checked to contain the `tagged origin`
string first. Fresh phone activity was made by launching apps over `adb` and firing the watcher's
`net.activitywatch.android.watcher.LOG_DATA` broadcast, then **Sync Now** on each device was tapped
by `adb shell input` after locating the button with `uiautomator dump` (a first mis-tap opened the
directory chooser — backed out with two `KEYCODE_BACK`, `syncDirUri` never touched, "Directory:
ActivityWatch-sync" unchanged throughout).

**Result:**

```
phone   13:56:01  = Synced 4 new events        (push into staging)
phone   13:56:01  SAF export: jude_s_s25_ultra/ad0c6c34-… copied=1
tablet  13:58:48  = Synced 1 new events, tagged origin ad0c6c34-d388-4ef0-b906-976bd760b22d
tablet  13:58:49  Multi-Device Sync completed: success=true
```

`ad0c6c34-…` is the phone's `files/device_id`, read directly via `run-as`; the tablet's own is
`7b54cfe9-…`. Then the tablet's `sqlite.db` (+ WAL) was pulled and queried directly:

- 16 imported events now carry `$aw.origin.device`, across all four
  `aw-watcher-android*-synced-from-jude_s_s25_ultra` buckets;
- the **only** distinct origin UUID in the whole tablet store is the phone's;
- the tablet's own first-hand `aw-watcher-android` bucket — 715 events — carries **zero** tags.

That is R11 (a device never stamps its own data), the log line, and the stored-data check all
satisfied. The `! Bucket hostname/device ID was invalid` warning fired on the phone's push and the
sync carried on instead of panicking — item 5 of 3.1 (the `src_did.unwrap()` fix) exercised too.
Nothing now blocks **3.2**.

### 2026-09-09 (night) — 3.1: imported events now say which device they came from
Phase 3's first step, and the smallest one: an event copied in from another device carries the
UUID of the device that collected it, in `$aw.origin.device`. Everything after it in Phase 3 rests
on being able to ask an event "whose is this?" without parsing a bucket id or trusting a hostname.

The device is worked out from **where the file was**, not from anything the sending device wrote.
The shared folder is `<hostname>/<device uuid>/test.db`, so by the time a peer's database is opened
the answer is already in the path — `origin_from_db_path` in `aw-sync/src/util.rs` reads it, and
`sync_run` carries it alongside each remote datastore because that is the last point where the path
still exists. It travels down through `sync_datastores`' existing `src_did` argument, which the
import path had always passed `None`, so no public signature changed. Export is untouched: a device
never tags its own data, which is what keeps its raw record pristine (**R11**).

Two things fell out of it. `src_did.unwrap()` in the "bucket hostname is `unknown`" fixup would have
panicked on any import that hit it — `src_did` was always `None` there — and now warns instead.
And the import log line grew the origin: `= Synced 42 new events, tagged origin <uuid>`. That line
exists specifically so this step can be checked on a device at all; the tag lives inside event data,
which nothing in the UI is guaranteed to show, and app-private storage cannot be read over adb.

**What was decided and not built.** The owner asked whether to spend a step on a Sync Now button for
settings or a "last synced" indicator. It is the indicator: [1.7](#17--sync-settings-reachability-and-a-manual-trigger)'s
**Sync Now** already exists and, since 2.3, its cycle ends with the settings step — so the button
is there and works. What nobody can see is whether it did anything. Written up as
[2.3b](#23b--show-when-settings-last-synced); it does not block 3.2.

**Repos:** `aw-server-rust@beta` first, then the pointer in `aw-android@beta` — the habit the
submodule has already broken once.

#### How to test this

Nothing here is dangerous: no file is renamed, moved or deleted, and nothing writes to the
Syncthing folder that was not written every cycle already. **Do not rename the sync folder** —
the standing rule from D25/D26 — and nothing below asks you to.

**Only newly imported events get tagged.** The merge resumes from the newest event it already holds,
so history already on the tablet stays untagged by design. If you sync without making new activity
on the phone first, there will be nothing to see and that is not a failure. Step 2 exists for this.

1. Wait for the CI build of `aw-android@beta` to finish, and install that APK on **both** the phone
   (`SM-S938B`) and the tablet (`SM-X520`).
2. On the **phone**, open some app you do not normally use — anything — and use it for about
   three minutes, so there is fresh activity that has never been synced.
3. On the **phone**: open ActivityWatch ▸ **Settings** ▸ **Sync** ▸ tap **Sync Now**. Wait for it
   to finish.
4. Give Syncthing a minute to carry the file to the tablet.
5. Plug the **tablet** in and start watching its log. Run this on the PC, and leave it running:

   ```
   adb logcat -c && adb logcat -s aw-sync SyncInterface
   ```

6. On the **tablet**: open ActivityWatch ▸ **Settings** ▸ **Sync** ▸ tap **Sync Now**.
7. **PASS** is a line in that log reading, with a real UUID in place of the last field:

   ```
   = Synced 42 new events, tagged origin 3f1c9a20-....
   ```

   Any number of events is fine. What matters is that `tagged origin` is present and the UUID is
   the **phone's**, not the tablet's.
8. Confirm it is the phone's UUID. On the **phone**, with the phone plugged in:

   ```
   adb logcat -d -s SyncInterface | grep "Device id"
   ```

   **PASS**: the UUID it prints matches the one in step 7 exactly.
9. **FAIL** looks like any of these, and they mean different things — please say which one:
   - `= Synced 42 new events` with **no** `tagged origin` — the origin was not derived; the
     warning `Cannot tell which device ... belongs to` should be in the same log.
   - `✓ Already up to date!` and no synced line at all — nothing new arrived; step 2 or step 4
     did not do their job. Not a 3.1 failure.
   - `tagged origin` showing the **tablet's** own UUID — that would be a real bug and the most
     important thing to catch.
   - `Sync failed:` — unrelated breakage; send the whole line.
10. Optional, and the nicest confirmation if the view cooperates: on the **tablet**, open
    ActivityWatch ▸ the web view ▸ **Raw Data**, choose the bucket ending
    `-synced-from-<phone hostname>`, and look at the newest event's data. If it lists
    `$aw.origin.device`, that is the tag in the stored data rather than in a log line. If the view
    does not show data fields at all, skip it — that is not a failure, it just means aw-webui
    does not surface them.

**What to send back:** the `= Synced ... tagged origin ...` line from step 7, the `Device id` line
from step 8, and — if you did step 10 — a screenshot of the event's data.

### 2026-09-09 (evening, later) — the elegant fix did not work; the dull one does
The `ContentObserver` added an hour earlier never fired for the writes that matter. It registers
without complaint and does fire — for this app's own writes — which is exactly the shape of failure
that gets shipped: nothing throws, a log line appears during testing, and the feature does nothing.
Two measurements settled it. Syncthing delivering `meta.json` from the phone produced no
notification on the tablet in 75 seconds, though the file had demonstrably arrived. Writing into the
folder from `adb shell`, a different uid just like Syncthing's, produced none in 40 seconds.
`ExternalStorageProvider` does not tell an app about other apps' writes to a document tree and never
promised to.

It was removed rather than kept as decoration that might work on some other OEM, and replaced by a
30-second poll while the app is on screen, plus an immediate refresh on resume. Verified both
directions on hardware, including one round deliberately run in a window with no sync due, because
the first round proved only that a scheduled sync could do the job — the phone's cycle beat the poll
by one second and took the credit.

**Worth keeping in mind for Phase 3 onward:** a JVM test suite cannot reach any of this. The merge
logic behind it has 39 tests and all of them passed while the trigger in front of it did nothing.

### 2026-09-09 (evening) — 2.3 verified on two devices, and the trigger was too rare
The rename went from phone to tablet: `Media > Video` → `Media > Fun`, saved on the phone, applied
on the tablet, confirmed in the tablet's own datastore. The tablet's settings store had been
completely empty beforehand, so this exercised the fresh-device path — a device adopting an
established one's categories, not merely overwriting its own.

**The half worth having tested is the quiet one.** A settings sync that republishes what it accepts
looks identical to a working one from the outside, and only shows itself as a log file that grows
forever and two devices trading one edit back and forth. The tablet's next sync logged nothing at
all, the phone's `settings.jsonl` still held its original 8 lines, and the tablet never created a
`settings.jsonl` of its own. That is the `appliedSharedSettings` snapshot doing exactly the job it
was added for.

**What the test caught that no unit test could.** The tablet's sync cycle ran four seconds *after*
the phone published — and still saw nothing, because Syncthing had not delivered the file yet. The
next cycle was fifteen minutes away, so for a quarter of an hour the tablet showed the old name
while the new one sat in its own sync folder. Nothing in 2.3's routing was wrong; it simply was not
asked often enough. 2.3a adds a folder watcher and a refresh on app resume, which is the owner's
requirement in their words: no manual sync, no closing and reopening the app.

**A measurement trap, recorded because it wasted a diagnosis.** Syncthing preserves the source's
modification time on delivered files, so `stat` on the receiving device reports when the *sender*
wrote it. It says nothing about arrival, and it was briefly read as though it did — which made a
real race look impossible. Arrival time has to come from the receiver's own logs.

### 2026-09-09 (later still) — 2.3: settings sync, and the key space the doc had wrong
The shared store from 2.2 has its first user. Step 7 of every sync cycle publishes the shared
settings changed on this device and applies the ones changed elsewhere.

**The design document was wrong about the key space, and the code follows aw-webui instead.**
[`05`](05_DATA_MODEL.md) §5 assumed one shared key per categorised app — `category.com.google.
android.youtube` → `"fun"`. aw-webui has no such thing: the entire categorisation is one `classes`
value, an array of `{id, name, rule}` posted whole to `/api/0/settings/classes`. Renaming YouTube
*is* an edit to `classes`. Building the doc's key space would have meant translating in both
directions forever, against a format aw-webui is free to change, so §5 was corrected to describe
what ships. The `category.*` / `label.*` / `rule.*` namespaces are still routed — Phase 4's rules
are ours to write and will use them — they are simply empty today.

**What is shared is an allowlist, not a denylist.** R28 is the direction where a mistake is
expensive: aw-webui gains keys on its own schedule, and one about *this device* that leaked by
default would be noticed only after it had overwritten a peer's copy. `views` and `saved_queries`
were the interesting exclusions — they read like shared meaning, but they name buckets, and a
bucket id carries the hostname of the device that produced it, so copied across they point at
nothing.

**One piece of state the design had not anticipated.** "The owner changed this here" and "a peer
changed it and we have not applied it yet" are the same observation — local differs from the merge.
Only a record of what this device last *agreed to* separates them, so `AWPreferences` gained
`appliedSharedSettings`. Without it every device republishes every value it accepts, and two
devices trade one edit back and forth forever. The scenario tests exist mainly to hold that line:
each one runs a cycle, feeds the result back in as the next cycle's state, and asserts the second
cycle does nothing at all.

**Two new JNI functions** (`getSettings`, `setSetting`) in `aw-server/src/android/mod.rs`, which
means CI has to build before any of this can be tested — that file is behind
`#[cfg(target_os = "android")]` and the local checks cannot type-check it. It is now at least
covered by `check-local.sh syntax`, which parses cfg-gated files with rustfmt; it was only checking
`aw-sync`'s files and had no reason not to check this one too. `getSettings` hands values back as
raw strings rather than parsed JSON on purpose: parse-and-reprint would reorder object keys, and
two devices holding the same setting would then disagree forever about whether it had changed.

### 2026-09-09 (later) — 2.2: the shared logs, and a merge that does not care what order it reads
`SharedStore.kt` holds the append-only side of the shared folder: the record types for
`decisions.jsonl` and `settings.jsonl`, the merge that turns every device's copy into one answer,
and ULIDs for decision ids. `SharedFolder.kt` gained the SAF half — list the device directories,
read one device's log or all of them, append to our own.

**The whole point is one property, so it is what the tests assert.** Syncthing delivers files in
any order and arbitrarily late (R23), so the merge must depend on line *content* and nothing else
(R18). Rather than merging each fixture once, every merge test runs `assertOrderIndependent`,
which re-merges the same lines under 50 fixed shuffle seeds and once reversed, and fails if any of
them disagrees. Fixed seeds, not random ones — a merge that is order-dependent one run in thirty
is worse than one that fails every time.

Writing the tests that way immediately forced two rules [`05`](05_DATA_MODEL.md) §4.2 did not
state, both now written into it: **a duplicated `id` counts once** (a copied file could otherwise
out-vote the decision that actually won), and **`id` is the final tiebreak after `created_by`**
(two lines from one device in one millisecond in one group would otherwise be settled by whichever
was read first — exactly the dependence R18 rules out). A third, smaller one: a `created_at` that
will not parse loses to every one that does, instead of being guessed at.

Also carried the `VERSION` lesson forward without waiting to be bitten again: the `.jsonl` files
are created as `application/octet-stream`, because that is what stops a SAF provider renaming
`decisions.jsonl` to `decisions.jsonl.txt` the way it renamed `VERSION` this morning.

**Nothing calls any of it yet**, deliberately — 2.2 is the store, 2.3 is the first user of it. The
app's behaviour on device is byte-identical to 2.1's, so there is nothing to test on hardware and
no device check was asked for. `scripts/check-local.sh kotlin` and the full unit suite pass; 26 new
tests in `SharedStoreTest.kt`. Compaction ([`05`](05_DATA_MODEL.md) §5) is still not built, but
unknown line types are now kept verbatim so it can be built safely later.

### 2026-09-09 — 2.1 verified on device, after catching a SAF extension bug
Both devices synced against the fixed build (commit `5efe301`). Confirmed on `SM-S938B` and
`SM-X520`: one `VERSION` file at the shared-folder root reading `1`, `devices/<uuid>/meta.json`
present for both with `role: phone` / `role: tablet` correctly split and a fresh `last_seen`.

**The device test caught something the unit tests could not.** `ensureVersion()` created the file
as `createFile("text/plain", "VERSION")`. On this phone's storage provider that silently becomes
`VERSION.txt` — SAF maps `text/plain` to a preferred extension when the requested name has none.
`findFile("VERSION")` then never matched what was actually on disk, so every sync treated the
folder as version-less and wrote another copy: `VERSION.txt`, then `VERSION (1).txt`, then
`VERSION (2).txt` after two devices ran a couple of syncs apiece, all holding `1` but under names
nothing would ever read back. Nothing in `SharedFolderTest.kt` could have caught this — it is a
pure-JVM suite with no `DocumentFile`, and the bug is specifically in how a real SAF provider
renames a create. Fixed by writing `application/octet-stream` instead, which has no preferred
extension; `mirrorDirectory` already uses it for every other file for the same reason. `meta.json`
was never at risk — it already carries its own `.json` extension, and both devices' copies were
correct on the very first pass, before the fix.

The three stray files were deleted by hand from both devices' sync folders; no migration was
written, since 2.1 has not shipped past this pair of test devices.

### 2026-09-09 — 2.1: the shared folder gained a version, and each device a name
Phase 2 has code. `SharedFolder.kt` writes the root `VERSION`, publishes
`devices/<uuid>/meta.json` at the end of every cycle, and refuses the whole cycle — before the
import, not after — when `VERSION` is newer than this build or unreadable.

**The spec was wrong about where the database lives, and the spec lost.** `05` §2 drew
`events.db` inside `devices/<uuid>/`. Nothing has ever written it there: aw-sync's
`setup_local_remote` picks `<hostname>/<device_id>/test.db` and `find_remotes` reads it back from
the same place — the layout 1.2–1.5 verified on hardware. Moving it is a Rust change that would
cost every one of those verifications, so `devices/` went in beside the hostname directories and
§2 was corrected. The device now appears twice at the root — once by hostname, once by uuid — and
`meta.json` is what ties the two together.

**The consequence that would have bitten silently:** `devices/` sits at exactly the level the SAF
import walks looking for hostnames, so without `isSharedStateDir` every peer's `meta.json` would
be copied into app-private storage as though it were a peer's database directory, and `peers=`
would count one too many. Worth checking on device precisely because nothing would break — it
would just quietly do the wrong thing.

**A malformed `VERSION` refuses, the same as a newer one.** §8 only asked for the newer case, but
junk in that file is likelier to be a newer writer than an accident, and the point of the section
is that guessing is unrecoverable where refusing is not. §8 now says so.

**`parseDeviceMeta` deliberately has no `android.util.Log` call.** The first version logged the
unparseable case, and every JVM unit test touching it threw `RuntimeException` — this project has
no Robolectric, so `android.util.Log` is not mocked. The parse is pure now and the caller says what
a null means, which is better separation anyway.

**Owed on the next device pass:** `VERSION` at the root, `devices/<uuid>/meta.json` for both
devices, `SAF import: peers=1`. None of 2.1 has run on hardware.

### 2026-09-08 — 1.9's failure path finally ran, and it was adb-drivable after all
The last hole in Phase 1 is closed. On the tablet (`SM-X520`, `jude_s_tab_s10_fe`), a sync against
an unreachable folder now reports failure everywhere it should:

```
15:57:24.840  W  SAF directory not accessible or not a directory: …ActivityWatch-sync-MISSING
15:57:25.112  W  SAF directory not accessible or not a directory: …ActivityWatch-sync-MISSING
15:57:25.112  W  Multi-Device Sync completed: success=false, message=import failed: …; export failed: …
```

and the status line reads `Sync failed: import failed: sync folder unreachable (permission revoked,
or folder deleted); export failed: …` — the string 1.9 was written to produce.

**Three things worth carrying forward.**

**The message names both passes, not just the export.** 1.9's Result predicted
`Sync failed: export failed: …`. The real message joins the import *and* the export with `; `,
which is the collect-don't-throw design behaving correctly: the import failed, the cycle continued
to the export instead of aborting, and the user hears about both. 1.9 has been corrected in place.

**`failed=N` never appears on this path.** Both passes return at the `safDir` guard, before the
`SAF import:` / `SAF export:` lines that carry the counters. Do not detect this class of failure by
grepping for `failed=1`; it is reported through the message.

**The doc was wrong that adb cannot drive it — but the obvious way to drive it is dangerous.** The
sync folder is Syncthing-managed (`.stfolder`, `.stversions`), so renaming or deleting it to force
the failure would propagate the deletion to the phone. Instead the failure was induced by swapping
`syncDirUri` to a folder that does not exist, via `run-as`, and restoring it byte-identical
afterwards. Both peer databases were intact after the test and the healthy path was re-verified at
`16:01:19` (`failed=0`, export still before the callback). This exercises the *folder-unreachable*
half of `safDir == null || !safDir.isDirectory`, not a literal Settings revoke — same line, same
`result.fail()`, but the distinction is recorded rather than glossed.

**The trap, for next time:** `am force-stop` alone does not reload preferences. `BackgroundService`
is sticky, so a new process appears within a second or two, and editing `shared_prefs` *after* the
force-stop leaves that process holding the old values — the first attempt reported `success=true`
with the real URI while the file on disk said `-MISSING`. Edit prefs **first**, then force-stop,
then confirm a new pid, then drive the UI. `SyncSettingsActivity` is not exported, so reach it via
`monkey -c android.intent.category.LAUNCHER`, the drawer, then *Sync Settings*.


### 2026-09-04 (afternoon) — Measured on hardware: 8.3× more history is visible, nothing re-synced
The owner installed the 1.11 build on both devices (phone `15:29:32`, tablet `15:32:32`) and both
were checked directly rather than taken on trust — grepping each device's live `base.apk` for the
post-fix query string returns **8**, so the fixed webui is genuinely in the running app.

Then the actual measurement, taken through each device's own server over the full year, varying
*only* the merge keys #960 changed:

| Device | Bucket | old `["app","title"]` | new `["app"]` | |
|---|---|---|---|---|
| **Tablet** | `…-synced-from-jude_s_s25_ultra` | 48,186s | **399,383s** | **8.3×** |
| Phone | `aw-watcher-android` (own) | 67,542s | **418,760s** | 6.2× |
| Phone | `…-synced-from-jude_s_tab_s10_fe` | 4,563s | **50,138s** | 11.0× |

The tablet row is 1.11's stated check, and it passes: predicted `~15,503s → ~366,700s`, observed
`48,186s → 399,383s`. Both figures came in above the prediction for one reason — two days of events
accrued since the 09-03 baseline, and post-1.8 events carry titles, which raises the *old* query's
number too. The ratio is what the fix bought, and it cost no re-sync and no migration.

**Both stale-build worries are now closed by looking**, not by reasoning: the CI artifact was
grepped before install, and the installed APK was grepped on each device after. That mattered here
because the whole change is a submodule pointer — the one kind of change where "it built fine" and
"it shipped the new code" are genuinely different claims.

⚠️ **This measures the query engine, not a screenshot.** It is the same merge `canonicalEvents`
performs and that was returning near-zero; the rendering half rests on the fixed webui being in the
installed APK, which the grep establishes. Nobody has photographed the Activity page.

**Phase 1 now has exactly one hole left:** 1.9's *failure* path, which needs the UI and cannot be
driven from adb. 1.10 remains an owner decision. Phase 2 is unblocked.

### 2026-09-04 (midday) — The `aw-webui` bump: pointers moved, build verified, device not yet
Step **1.11**. Upstream fixed [aw-webui#959] the morning after it was reported ([#960], `85db7b5`),
and the only work left on our side was moving a submodule pointer — no code, no migration, no
re-sync. Done and pushed:

| Repo | From | To |
|---|---|---|
| `aw-webui` | `3cbe349` | `a2ca625` (`origin/master` head) |
| `Judemasic/aw-server-rust@beta` | `c6f7df2` | `9e01fab` |
| `Judemasic/aw-android@beta` | `bd39674` | `1524318` |

**Checked before moving, not assumed:** `git log origin/master..3cbe349` is empty, so the pin was a
clean ancestor and this fork carries no aw-webui changes of its own — a fast-forward, not a merge
with anything of ours at stake. `git merge-base --is-ancestor 85db7b5 origin/master` is true.

**Took master's head rather than `85db7b5` exactly**, which pulls in two more Android-relevant
fixes for free: **#966** replaces `prompt()` with modals (a `prompt()` call is a silent no-op in a
WebView, so whatever used it was dead in this app) and **#956** adds category JSON import over SAF.
Stopping at `85db7b5` would have meant bumping again within days.
**The push order mattered for the first time.** Every previous build was Kotlin-only, so the cache
trap in [`02`](02_ARCHITECTURE.md) §5 was theoretical. Here the submodule actually moves:
`aw-server-rust@beta` was pushed first and the pointer second. CI build [33874769211] came back
**green in 21m30s** — against a usual 10–17m, which is the cache miss doing exactly what it was
meant to.

**Then the cache trap was closed by inspection, not by inference.** "It probably rebuilt" is the
kind of claim that costs a day when it is wrong, and a stale `.so` looks identical to a fix that
did not work. aw-webui is embedded in `libaw_server.so`, so the artifact was downloaded and the
library grepped for the query string #960 introduces:

```
grep -c 'merge_events_by_keys(events, ["app"]);'  lib/arm64-v8a/libaw_server.so   → 2
```

`git grep` puts that string at `a2ca625:src/queries.ts:173` and nowhere in `3cbe349`, so its
presence in the binary can only mean the new webui was compiled in. Delivery is now verified end to
end.

⚠️ **Nothing has been observed on a device.** What is verified is the build and its contents; what
is claimed is that ~96% of the phone's history stops being hidden. Those are not the same
statement, and the second is the one that matters. **Next session's first move:** install, open
Activity for the synced phone bucket, and see whether **15,503s** moves toward **366,700s**. If it
does not, delivery is already ruled out — the answer will be in the query or in which bucket the
view is reading.

### 2026-09-03 — Phase 1 complete: cross-device sync verified end to end
Tablet attached over adb. Its server lists `aw-watcher-android-synced-from-jude_s_s25_ultra`
(hostname `jude_s_s25_ultra`, **6,797 events**) beside its own 485 — a bucket it could not have
produced. The Activity query against that host returns **15,503.1s**, so **1.5's "device A displays
a bucket only device B could have produced" is satisfied.** Phase 1 closes.

The same measurement quantified 1.8's caveat across sync: `flood` alone yields 366,699.9s, the
`["app", "title"]` merge yields 15,503.1s. **4.2%.** The other 96% is pre-fix phone history —
already on the tablet, invisible only because of aw-webui#959. An upstream fix reveals all of it
without a re-sync.

Also corrected a **wrong claim in this document**: 1.8 stated the regression shipped in
**v0.14.0b2** with "nine days of exposure". It does not. Verified by ancestry rather than dates —
`git merge-base --is-ancestor bf0fc84 749585f` returns false, and `749585f` is what v0.14.0b2 pins
through `aw-server-rust e8e6e90`. Released users are unaffected; only master builds carry it. The
corrected chain is now a table in 1.8 with the command to reproduce it.

Reported upstream as **aw-webui#959**, filed against aw-webui rather than aw-android because the
faulty query lives there. Found while reading aw-android#247: upstream **PR #251** is open and
touches the same four files as **1.7**, so expect a conflict — and it fixes the
success-reported-on-failure bug this fork still has.

*Newest first.*

### 2026-09-02 (latest) — The Activity view was never a UI problem
The owner: *"still nothing in activity … you can see the timeline but it says time active 0"*.
Three queries against the same bucket and day on the tablet settled it:

```
flood(query_bucket("aw-watcher-android"))            -> 4878.8s
  + merge_events_by_keys(events, ["app", "title"])   -> 0.0s     ← what aw-webui runs
  + merge_events_by_keys(events, ["app"])            -> 4878.8s
```

`merge_events_by_keys` drops every event missing a requested key. Android events carry
`{app, package, classname}` and no `title`, so a whole day evaluates to zero.

**It is an upstream regression, not ours.** aw-webui `bf0fc84` (2026-07-24), an iOS ScreenTime
patch, changed the shared Android branch from `["app"]` to `["app", "title"]` — ScreenTime events
have a title, Android's never have. Still on aw-webui master at 2026-08-31, reported nowhere.

**Why nobody noticed, checked rather than assumed:** the previous aw-android release was
2026-07-23, one day *before* the regression. The first release carrying it is **v0.14.0b2
(2026-08-24)** — nine days, not six weeks. And the Timeline still works, so nothing looks broken
unless you open Activity.

Fixed in **1.8** by emitting `title` from the watcher rather than patching aw-webui: two forks
instead of three, and it survives the query changing again.

**Upstream is hitting the same walls we are.** Issue **#247** (2026-08-31, user feedback on
v0.14.0b2) independently reports three problems this fork had already fixed: no way to discover the
drawer, no sync feedback of any kind, and sync writing nothing to the chosen folder. **#218** is a
second user who could not reach Sync Settings. That is strong evidence these were real defects
rather than local misconfiguration — and it is why Phase 6 now carries a concrete upstreaming list.

**Two plan decisions the owner made this session:**
- **Q4 resolved — Rust pipeline, native phone-first view first, aw-webui view later.** Driven by
  the stated end state: every device, including the PC, answering "what was I doing then". A Kotlin
  pipeline would strand that on Android.
- **Phase 5 cut** to "reachable and not broken". The effort moves to 3.4.

### 2026-09-02 (late night) — **Cross-device sync works.** Phase 1's premise is proven
The owner tapped **Sync Now** on the tablet with the phone's data sitting in the Syncthing folder:

```
SAF import: peers=2 copied=2 skipped=0
= Synced 728 new events
= Synced 6280 new events
Multi-Device Sync completed: success=true
Manual sync finished: success=true
```

~7,000 events crossed from the phone to the tablet. **Blocker 1 is dead**, 1.3 finally ran with
more than one database, and 1.7's button paid for itself on its first use — the alternative was
waiting out a 15-minute timer.

**And the first real run immediately found a bug that no amount of reading would have.** `peers=2`,
for one phone. Pulling from a peer calls `setup_local_remote(<peer hostname>, our_device_id)`,
which creates `<peer hostname>/<our device id>/test.db` locally as a side effect of *reading*. The
export scanned every hostname directory for our device id — chosen to survive a device rename — and
published our database into the peer's hostname folder. Two devices, four directories. Fixed in
**1.4a** by exporting only the current hostname's copy. Single-writer (R20) was never violated, so
this was cost and confusion rather than corruption — but it is quadratic, and it would have looked
like phantom devices forever.

**Upstream drift closed the same session:** merged all 7 commits from `ActivityWatch/master` on a
feature branch, then into `beta`. **#249 was refused**, exactly as [`03`](03_SYNC.md) §2.5 warned —
its Kotlin half declares `external fun setDataDir` with no matching export in this fork's
`android.rs`, which would have reproduced Blocker 6 precisely. The refusal is now a comment at the
call site so the next merge re-checks it rather than rediscovering it. The submodule pointer stayed
on our fork. **#250 is a gift:** it replaces the hardcoded `/#/activity/unknown/` route with the
real hostname, which is the cause of the empty Activity view the owner reported.

Drift is now **0 behind / 27 ahead**.

### 2026-09-02 (night) — 1.4 and 1.7 on the tablet: runs clean, proves half of what it needs to
CI green, installed over the top (`firstInstallTime` unchanged, so `syncEnabled` and the SAF
`syncDirUri` survived again — second confirmation that debug signing is fixed). The first scheduled
sync after install:

```
SAF import: peers=0 copied=0 skipped=0
= Synced 18 new events / = Synced 2 new events
Multi-Device Sync completed: success=true
SAF export: copied=1 skipped=0
```

**What this settles:** the import pass executes and is a correct no-op with no peers, the narrowed
export still copies exactly what the old whole-tree mirror did (`copied=1`, unchanged), and neither
inflation-time UI change crashes `MainActivity`.

**What it cannot settle:** everything the step is actually for. `peers=0` is the expected reading on
a lone device, so the import path has still never copied a byte, and 1.3 still has one database to
choose from. Both need the phone. A single-device green run here is exactly the kind of result that
looks like progress and proves nothing — same trap as the `{"success": true}` no-op in Blocker 2.

**Next:** install on the phone, let Syncthing carry the tablet's `.db` over, then watch for
`SAF import: peers=1`. That is 1.5, and it is the first check in this project that can fail
usefully.

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
