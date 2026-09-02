# 03 — Sync: Diagnosis & Design

> **Status: sync is completely non-functional.** Not degraded — non-functional. No `.db` file
> reaches the shared folder, and even if one did, no other device would ever read it.
>
> This document explains exactly why, then specifies the design that replaces it.
> Findings below were read out of the code on **2026-09-02**; each names the file to check.

---

## 1. Symptom

> *"the sync is not working right now, there is no db in the shared folder. I activated the sync on
> both and I have Syncthing pointed at the same folder on both devices."*

Syncthing is configured correctly. The problem is entirely on the app side, and there are **four
independent blockers**. Fixing any three of them still yields nothing working.

---

## 2. The four blockers

### 2.1 Blocker 1 — the mirror is one-way *(the architectural one)*

`SyncInterface.kt` sets `AW_SYNC_DIR` to the **app-private** directory
(`getExternalFilesDir(null)/sync`). The Rust engine reads *and* writes there. After each sync,
`copySyncFilesToSafDir()` copies **app-private → shared folder**.

Nothing, anywhere, ever copies **shared folder → app-private**.

```
app-private syncDir  ──── copySyncFilesToSafDir() ───►  SAF shared folder ──► Syncthing ──► peers
                     ◄──────────  NOTHING  ─────────────
```

So when `pull_all_from_all_hostnames()` scans `AW_SYNC_DIR` looking for other devices, it is
scanning a directory that **only ever contains this device's own output**. Peer databases land in
the SAF folder and are never brought back in.

> **Consequence: even with everything else fixed, Device A can never see Device B's data.**
> This is the blocker that makes the other three moot. Violates **R21**.

**Fix:** the mirror becomes bidirectional — an **import** pass (SAF → app-private, everyone
*else's* device directories) before the pull, and the existing **export** pass (app-private → SAF,
*only* this device's own directory) after the push. Never import your own directory over your own
live data.

---

### 2.2 Blocker 2 — push and pull disagree about directory depth

`push_with_hostname_and_device_id()` (`sync_wrapper.rs`) builds:

```
<sync>/<hostname>/<device_id>_staging/
```

and hands that to `sync_run` → `setup_local_remote(path, device_id)`, which does
`path.join(device_id).join("test.db")` (`sync.rs:168-175`). The database therefore lands at:

```
<sync>/<hostname>/<device_id>_staging/<server_device_id>/test.db     ← 3 levels deep
```

But the readers expect **2 levels**:

- `pull_from_hostname()` scans `<sync>/<host>/<dir>/*.db` — looks one level too shallow, finds nothing.
- `get_remotes()` (`util.rs:195`) filters hostname directories through
  `contains_subdir_with_db_file()`, which checks for a `.db` **directly** inside a subdirectory
  (`util.rs:181-190`). With the extra `_staging` level, it returns `false`.

> **Consequence:** `get_remotes()` returns an **empty list**, so `pull_all_from_all_hostnames()`
> iterates over nothing and returns success having done **absolutely nothing**. A silent no-op
> that reports `{"success": true}`.

**Fix:** drop the `_staging` level. Push directly to `<sync>/<hostname>/`, letting
`setup_local_remote` create the `<device_id>/` level itself. That restores the layout every reader
in the codebase already expects: `<host>/<device_id>/test.db`.

---

### 2.3 Blocker 3 — device IDs are not unique

`SyncInterface.getDeviceId()`:

```kotlin
val installerPackage = packageManager.getInstallerPackageName(packageName)
if (installerPackage != null && ...) {
    return "android_${installerPackage.hashCode().toString().take(12)}"
}
val fingerprint = android.os.Build.FINGERPRINT ?: "unknown_fingerprint"
return "android_${fingerprint.hashCode().toString().take(12)}"
```

Neither branch identifies a *device*:

- **Installer package** is `"com.android.vending"` for every Play install and `null` for every
  sideload. Two devices installed the same way hash to the **same string**.
- **`Build.FINGERPRINT`** identifies a *build* — model + version + build number. Two phones of the
  same model on the same OS version are **identical**.

Since APKs here are sideloaded from the same CI artifact, both devices take the fallback branch and
produce **the same ID**.

> **Consequence:** both devices claim the same directory and overwrite each other — reintroducing
> exactly the data loss the per-device design was created to prevent. Violates **R19** and **R22**.

**Fix:** generate a `UUID.randomUUID()` **once**, on first run, and persist it in
`AWPreferences`. It is then unique by construction, stable across upgrades, and survives a device
rename. (`Settings.Secure.ANDROID_ID` is an alternative but is scoped per signing key and resets on
factory reset — a stored UUID is simpler and strictly better here.)

---

### 2.4 Blocker 4 — upstream's pull still discards data

`sync_wrapper.rs::pull()` retains upstream's behaviour:

```rust
if dbs.len() > 1 {
    warn!("More than one db found in sync folder for host, choosing largest db");
}
let db = dbs.into_iter().max_by_key(|e| e.metadata().map(|m| m.len()).unwrap_or(0))
```

When several devices share a hostname, everything but the largest file is **silently dropped**.

> Directly violates **R19**. Even once blockers 1–3 are fixed, this remains a live data-loss path
> the moment two devices report the same hostname — which is realistic, since hostname derives from
> the device name and two phones can easily both be `"phone"`.

**Fix:** iterate **every** discovered database and sync from each. Never select by size.

---

### 2.5 Also noted

- `pull_from_hostname()` computes a `device_id` local and never uses it — dead code, remove.
- `fix_hostname_migration.md` (now deleted) chased hostname migration as the cause of the missing
  `.db`. It was not the cause; blockers 1 and 2 fully explain the symptom. Do not re-open that
  thread.
- The earlier `UnsatisfiedLinkError` and `SIGABRT` crashes **were** real and **are** fixed
  (`catch_unwind` + `android_logger` in `android.rs`). They masked these four blockers rather than
  causing them.

---

## 3. Target design

### 3.1 Shared-folder layout

Per **R20**, every file has exactly one writer — the device named in its path.

```
<shared folder>/                      ← the Syncthing folder
  devices/
    <device_uuid_A>/
      meta.json          ← A's name, platform, last-seen        (writer: A)
      events.db          ← A's datastore snapshot                (writer: A)
      decisions.jsonl    ← decisions made on A, append-only      (writer: A)
      settings.jsonl     ← settings changed on A, append-only    (writer: A)
    <device_uuid_B>/
      …
```

No file is ever written by two devices, so **Syncthing never produces a `.sync-conflict-*` copy**.
Combining happens at read time by reading every device's files. Full format in
[`05_DATA_MODEL.md`](05_DATA_MODEL.md).

> **This replaces the previous plan's single shared `user_decisions.json`.** A single file written
> by every device would have generated conflict copies continuously — Syncthing merges files by
> replacement, never by content. Append-only per-device logs avoid the problem structurally rather
> than trying to resolve it after the fact.

### 3.2 The sync cycle

```
1. EXPORT SELF     app-private <sync>/<host>/<self>/  ──►  SAF devices/<self>/
                   (own directory only)

2. IMPORT PEERS    SAF devices/<other>/  ──►  app-private staging
                   (every directory except self; copy, never open in place — R24)

3. PULL            aw-sync reads each imported peer db, merges events into the local datastore
                   (every db, never "largest" — R19)

4. PUSH            local datastore ──► app-private <sync>/<host>/<self>/test.db

5. EXPORT SELF     repeat step 1 so the push is visible to peers
```

Steps 1 and 5 write **only** this device's directory. Step 2 reads **only** others'. That
separation is what upholds R20 across the SAF boundary.

### 3.3 SQLite safety (R24)

Syncthing replaces files by writing a temp file and renaming over the target. A database being
read at that moment can return corrupt pages or fail outright.

**Rule: never open a database in the SAF folder.** Import copies it to app-private storage first,
and only the copy is opened. The live local `test.db` is never in the SAF folder, so Syncthing can
never touch it.

This makes the previous plan's "close all datastore connections before pull, reopen after" step
unnecessary — no new Rust datastore lifecycle API is needed, which removes the riskiest item from
the old plan.

---

## 4. Fix checklist

| # | Fix | Where | Rule |
|---|---|---|---|
| 1 | Bidirectional SAF mirror — add the peer-import pass | `SyncInterface.kt` | R21 |
| 2 | Drop the `_staging` level; push to `<host>/<device_id>/` | `sync_wrapper.rs` | — |
| 3 | Persisted `UUID` device ID | `SyncInterface.kt`, `AWPreferences.kt` | R22 |
| 4 | Pull from **every** db, never the largest | `sync_wrapper.rs` | R19 |
| 5 | Import to app-private before opening | `SyncInterface.kt` | R24 |
| 6 | Remove dead `device_id` local | `sync_wrapper.rs` | — |

Sequenced as Phase 1 in [`06_ROADMAP.md`](06_ROADMAP.md).

---

## 5. How to verify sync actually works

Nothing here is proven until this passes on **two real devices**:

1. Install the APK on both; enable sync; point both at the same Syncthing folder.
2. Within ~15 minutes, confirm the shared folder contains **two** `devices/<uuid>/` directories
   with **different** UUIDs. *(Catches blockers 1, 2 and 3 at once — this is the single highest-value
   check in the project.)*
3. Confirm each device's `events.db` is non-zero and growing.
4. On device A, query a bucket that only device B could have produced. Data present ⇒ the pull path
   works end to end.
5. `adb logcat -s SyncInterface aw-sync` — no `UnsatisfiedLinkError`, no `SIGABRT`, no
   `"choosing largest db"`.
