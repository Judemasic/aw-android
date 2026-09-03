# 06 — Roadmap

> **👉 START HERE:** ✅ **Phase 1 is done — begin Phase 2.**
> Cross-device sync works end to end and is verified on hardware: the tablet holds **6,797 events
> the phone produced**, under `aw-watcher-android-synced-from-jude_s_s25_ultra`, and the Activity
> view renders them. The combined timeline is now unblocked — it has real multi-device data *and*
> a display path that has been proven, which is what 1.5 was gating on.
>
> **🔥 DO THIS FIRST — bump `aw-webui` and get ~96% of the history back.** Upstream fixed
> [aw-webui#959] on 2026-09-03 (**[#960], merged `85db7b5`**), so Android no longer needs a `title`
> to appear in the Activity view. The events are **already on both devices**; only the dashboard
> was hiding them. No re-sync, no data migration — it is a submodule bump.
>
> Our chain is `aw-android → Judemasic/aw-server-rust@beta (c6f7df2) → aw-webui (3cbe349)`, and
> `3cbe349` predates the fix. Verified: `git merge-base --is-ancestor 85db7b5 origin/master` is
> true in aw-webui, so it is on master.
>
> ```bash
> # 1. in aw-server-rust/aw-webui — move to a commit containing the fix
> cd aw-server-rust/aw-webui && git fetch origin master && git checkout <sha ≥ 85db7b5>
> # 2. commit the pointer in the fork and PUSH IT FIRST (02 §5, cache trap)
> cd .. && git add aw-webui && git commit && git push origin beta
> # 3. then bump aw-android's pointer, push, and run CI
> cd .. && git add aw-server-rust && git commit && git push origin beta
> ```
> ⚠️ **The submodule moves this time**, so the push order above is not optional — pushing the
> pointer without pushing `aw-server-rust@beta` first gives CI a cache hit and an APK with stale
> `.so` files. Expect **1.5's 15,503s to jump toward 366,700s** once installed; that is the check.
>
> **Then:** the failure path of **1.9** is still untested — revoke the sync folder permission, tap
> **Sync Now**, expect `Sync failed:` (see 1.9). And **1.10** needs an owner decision, below.
>
> The shared folder was checked on 2026-09-03 and is **clean** — one directory per device. Two
> stale databases remain in app-private storage; only one is a real leftover, and 1.5 says which.
>
> **Waiting on the owner, not on code: [1.10](#110--timeline-truncates-every-peers-name-at-the-first-_).**
> The Timeline labels every peer `android-synced-from-jude` because aw-webui cuts the hostname at
> its first underscore. **You do not have to rename to work around this** — the Timeline's
> `Filters ▸ Host:` dropdown reads the untruncated `hostname` metadata and already distinguishes
> the devices. Renaming to underscore-free names (`S25U`, `Tab-S10FE`) fixes the *label* too, but
> costs: the old sync directory is stranded as a phantom peer, **and already-synced buckets keep
> their old ids**, so history splits across `…-synced-from-jude_s_s25_ultra` and
> `…-synced-from-s25u`. Read 1.10 before deciding.
>
> **The 4.2% ceiling is not ours to lift.** Only events recorded after the **1.8** title fix reach
> the Activity view — 15,503s of the phone's 366,700s. The rest is already synced and sitting on
> the tablet; it becomes visible the moment **[aw-webui#959]** is fixed upstream, with no re-sync.
> Do not build a workaround for it.
>
> Everything pending is Kotlin-only; the submodule has not moved, so no `aw-server-rust@beta` push
> is needed — if it ever does move, push it **first** ([`02`](02_ARCHITECTURE.md) §5, cache trap),
> then the pointer, then build.

[#251]: https://github.com/ActivityWatch/aw-android/pull/251
[aw-webui#959]: https://github.com/ActivityWatch/aw-webui/issues/959

**How to work this document:** do one step, run its check, stop. Then update the step in place —
mark it `✅ DONE (date)`, write a **Result** saying what is *actually true now*, and flag with ⚠️
anything not verified. Append to the Progress Log at the bottom, newest first.

---

## Phase 1 — Make sync work *(blocking everything)*

Fixes the blockers in [`03_SYNC.md` §2](03_SYNC.md). Nothing here is new functionality; it is what
has to be true before any feature exists.

> **Status 2026-09-03:** ✅ **Phase 1's goal is met: cross-device sync works end to end on
> hardware**, and every step 1.0a–1.8 is verified on device. **1.9 is the one exception** — it was
> written after the fact, fixes error *reporting* rather than sync itself, and has not run yet.
> Cross-device sync moves data *and* the dashboard shows it. The one caveat that survives is
> **1.8's**, now measured across sync: only events recorded *after* the title fix are visible to
> the Activity view, which today is **4.2%** of the phone's history.
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
> | 1.9 | ✅ on device | `failed=0` column live on both devices; export now precedes the callback |
> | 1.10 | 🔎 found | Timeline truncates peer names at the first `_` — upstream, not fixed |

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

### 1.9 — A sync that failed must not report success ✅ VERIFIED ON DEVICE 2026-09-03
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

⚠️ **The failure path is still untested.** Everything above proves a *healthy* sync is unaffected;
none of it exercises `failed > 0`. **Check:** revoke the sync folder permission in Android
settings, tap **Sync Now**, and see `Sync failed:` with a reason naming the folder — not
`Sync complete`. That needs the UI; it cannot be driven from adb (§5.3).

[PR #251]: https://github.com/ActivityWatch/aw-android/pull/251

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

### 3.4 — Combined view with shading ⬜ ← *the screen the owner actually wants*
Render the combined track above per-device tracks; shade unresolved contention. *(R8)*
**Q4 is resolved (2026-09-02): native, phone-first, on top of the Rust pipeline from 3.2.** An
aw-webui view comes later for desktop. Born mobile-first per **R33** — it never joins Phase 5's
backlog.

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

> ## ✂️ Scope cut 2026-09-02 — this phase shrinks to "reachable and not broken"
> The owner, on seeing aw-webui on the phone: *"the UI is not usable on the phone at all … I do
> think the intended way to use it is with a browser."*
>
> **Do:** 5.3 (kill horizontal scroll) and what 1.7 already landed. **Do not:** a general pass to
> make aw-webui's desktop screens pleasant on a phone. That effort goes to **3.4** instead, which
> is native, phone-first, and the screen actually opened every day (Q4).
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
