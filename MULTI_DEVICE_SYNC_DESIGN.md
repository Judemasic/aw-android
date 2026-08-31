# Multi-Device Sync Design Document

> **Project**: ActivityWatch Android (`aw-android`)
> **Goal**: Enable sync across multiple Android devices showing a unified timeline, with user-controlled conflict resolution and no data loss.
> **Approach**: Leverage Syncthing for file transfer + intelligent merge + per-conflict UI decisions stored in the sync folder so they propagate to all devices.

---

## 1. Current Architecture (what we have)

### 1.1 Android App Structure
| Component | Location | Role |
|-----------|----------|------|
| `RustInterface.kt` | `mobile/src/main/java/.../RustInterface.kt` | JNI bridge to `libaw_server.so` (starts server, inserts events, queries data) |
| `SyncInterface.kt` | `mobile/src/main/java/.../SyncInterface.kt` | JNI bridge to `libaw_sync.so` (sync push/pull callbacks + SAF mirroring) |
| `BackgroundService.kt` | `mobile/src/main/java/.../BackgroundService.kt` | Foreground service orchestrating data collection |
| `SyncScheduler.kt` | `mobile/src/main/java/.../SyncScheduler.kt` | 15-minute sync timer (Handler + AlarmManager) |
| `SyncWorker.kt` | `mobile/src/main/java/.../SyncWorker.kt` | WorkManager worker for actual sync execution |
| `SyncSettingsActivity.kt` | `mobile/src/main/java/.../SyncSettingsActivity.kt` | UI to enable sync + choose SAF directory |

### 1.2 Data Collection Flow
```
Android UsageStatsManager.queryEvents()
    → SessionParser.parseEventsIntoSessions() [foreground state machine]
        → List<AppSession>
            → RustInterface.heartbeat("aw-watcher-android", json, 0.0)
                → JNI → libaw_server.so → Datastore::heartbeat()
                    → SQLite (app-private: files/data/test.db)
```

### 1.3 Embedded Server (`aw-server-rust`)
- Runs on `localhost:5600` in the same Android process
- Rust server with SQLite-backed datastore
- Full REST API for buckets, events, queries
- JNI bindings for Android in `aw-server/src/android/mod.rs`

### 1.4 Existing Sync Infrastructure (`aw-sync`)
| File | Role |
|------|------|
| `sync.rs` | Core engine: `SyncMode { Push, Pull, Both }`, `sync_datastores()`, `sync_one()` |
| `accessmethod.rs` | Trait `AccessMethod` — unified interface over Datastore (local) and AwClient (HTTP) |
| `sync_wrapper.rs` | Public API: `pull()`, `push()`, `pull_all()` |
| `android.rs` | JNI bindings for Android (5 exported functions) |
| `util.rs` | Remote discovery, server config parsing |

---

## 2. The Problem (confirmed in code)

### 2.1 Silent Data Loss (`sync_wrapper.rs` lines 35-46)
```rust
if dbs.len() > 1 {
    warn!("More than one db found in sync folder for host, choosing largest db");
}
let db = dbs.max_by_key(|entry| entry.metadata().map(|m| m.len()).unwrap_or(0))...
```

**What happens**: When two devices both push their `test.db` to the same sync folder (via Syncthing), only the **largest file is kept**. The smaller device's events are silently lost. This is the exact data loss scenario you're worried about.

### 2.2 Sync is Disabled by Default
- `AWPreferences.isSyncEnabled()` defaults to `false`
- Users must explicitly enable sync AND configure a SAF directory
- Comment in code: *"the sync directory is not accessible to other apps, so auto-sync would silently no-op"*

### 2.3 No Conflict Resolution
- No built-in mechanism at all
- The aw-sync README says: *"We avoid implementing conflict resolution by enforcing that each device only writes to files it owns, and other devices may not modify them."*
- Bucket ID conflicts are never detected
- Events deleted on one device won't be reflected on another

### 2.4 No Cross-Device Timeline
- `DayTimeline` is single-device — no origin_device field per session
- The unified view doesn't exist yet (timeline rendered by embedded aw-webui, which queries a single local datastore)

---

## 3. Proposed Solution Architecture

### 3.1 Design Principles
1. **No data loss ever** — keep all events from all devices, never silently discard
2. **Syncthing does the file transfer** — we only solve the merge/conflict problem at the app level
3. **User decisions persist and sync** — conflict choices stored in a `.json` file that Syncthing propagates to all devices
4. **Minimal Rust changes** — work primarily with the existing `aw-sync` engine, add merge logic rather than replacing it

### 3.2 Directory Structure (in the Syncthing shared folder)
```
<sync_root>/
  <hostname>/
    device1_test.db          ← Device A's raw datastore
    device2_test.db          ← Device B's raw datastore (written alongside, never overwritten!)
    user_decisions.json       ← Conflict resolution choices (syncs to all devices)
    _metadata.toml            ← Server config (port, api_key, device_id) per existing aw-sync
```

**Key change**: Each device writes to its own named file (`<device_id>_test.db`) instead of all writing to the same `test.db`. Syncthing merges these files. Our app then reads ALL `.db` files and combines them at query time.

### 3.3 High-Level Sync Flow

```
Device A                          Sync Folder (Syncthing)              Device B
  │                                     │                                  │
  │ Write events to deviceA_test.db     │                                  │
  │                                     │ ←─── Syncthing sync ───────────→ │ Read all .db files
  │ Push: copy deviceA_test.db →       │                                  │ Pull all .db files
  │   <hostname>/deviceA_test.db       │←──── Syncthing sync ──────────│ Push: copy deviceB_test.db →
  │                                     │                                  │   <hostname>/deviceB_test.db
  │                                     │                                  │
  │ READ PHASE (merge layer):          │                                  │
  │ 1. Scan all *.db in sync folder    │                                  │
  │ 2. For each bucket, collect events │                                  │
  │    from ALL devices                │                                  │
  │ 3. Check user_decisions.json for   │                                  │
  │    pre-existing conflict choices   │                                  │
  │ 4. Merge: combine all events,       │                                  │
  │    deduplicate by timestamp+content │                                  │
  │ 5. Show conflicts if detected      │                                  │
  │ 6. Present conflict UI to user     │                                  │
  │ 7. Save decision → user_decisions.json → syncs to B
  │                                    │
  Result: Unified timeline from both devices on BOTH phones
```

### 3.4 Conflict Detection Logic

**What counts as a conflict?** Two scenarios:
1. **Same timestamp range, different data** — Device A says "app X was active at 10:00" but Device B says "app Y was active at 10:00"
2. **Different timestamps that overlap when merged** — events from both devices fall in a time gap where one device has no data

**Detection algorithm:**
- For each bucket, collect all events from all `.db` files
- Group events by `timestamp_ms` (rounded to nearest minute for grouping)
- If two different devices both have activity data covering the same 1-minute window → conflict
- Mark these time ranges as conflicting

**Non-conflicts (handled automatically):**
- Events at different timestamps from different devices → just merge them into timeline
- Same event on both devices → deduplicate by timestamp + duration + data content
- Device B has data where Device A is silent → just add to timeline, no conflict

### 3.5 Conflict Resolution UI Flow

```
Conflict detected: "Device A and Device B both recorded activity at 2026-01-15 14:30"

┌─────────────────────────────────────────────┐
│  ⚠ Sync Conflict (from today's sync)       │
├─────────────────────────────────────────────┤
│ Device A:                                    │
│   📱 Chrome — social-media.com (12 min)     │
│                                              │
│ vs                                             │
│                                              │
│ Device B:                                    │
│   💬 WhatsApp — chat with Alex (8 min)      │
│                                              │
│ Resolution:                                  │
│   [ Keep A + Merge B's remaining data ]     │  ← Recommended
│   [ Keep B + Merge A's remaining data ]     │
│   [ Merge Both (keep all data) ]            │
└─────────────────────────────────────────────┘

→ Decision saved to user_decisions.json
→ Syncthing syncs it to Device B automatically
```

### 3.6 User Decision Store Format (`user_decisions.json`)
```json
{
  "version": 1,
  "decisions": [
    {
      "bucket_id": "aw-watcher-android",
      "time_window_start": "2026-01-15T14:30:00Z",
      "time_window_end": "2026-01-15T14:39:00Z",
      "conflict_devices": ["device_A_id", "device_B_id"],
      "resolution": "merge_all",
      "resolved_at": "2026-01-16T10:00:00Z"
    }
  ]
}
```

**Resolution types:**
| Value | Meaning |
|-------|---------|
| `"keep_source"` | Keep all data from `source_device`, drop data-only-on-target for this window |
| `"merge_all"` | Keep all events from both devices (no deletion) |
| `"manual_review_needed"` | User hasn't decided yet; event stays in conflicts list |

---

## 4. Implementation Plan — Phased Approach

### Phase 1: Per-Device DB Writes (Foundation)
**Files to modify:**
- `aw-server-rust/aw-sync/src/util.rs` — `find_remotes()`, `get_remotes()` logic
- `aw-server-rust/aw-sync/src/sync_wrapper.rs` — push/pull functions
- `aw-server-rust/aw-sync/src/sync.rs` — `sync_one()`, `sync_datastores()`

**Changes:**
1. Modify sync to write each device's data to `<device_id>_test.db` instead of `test.db`
2. Update `push_with_hostname()` to include the local device_id in the filename
3. Each device reads ALL `.db` files from the sync folder (not just its own)

### Phase 2: Multi-DB Merge Engine
**New Rust files:**
- `aw-server-rust/aw-sync/src/multi_sync.rs` — merge logic (Rust library)
- `aw-server-rust/aw-sync/src/conflict_detector.rs` — conflict detection algorithm
- `aw-server-rust/aw-sync/src/user_decisions.rs` — read/write user decision store

**Kotlin additions:**
- `ConflictResolver.kt` — bridge between Rust merge engine and Android UI
- `MultiDeviceTimelineQuery.kt` — query all DBs, return merged timeline with origin_device tags

**Merge logic (`multi_sync.rs`):**
```rust
pub struct MergeResult {
    pub merged_buckets: HashMap<String, Vec<Event>>,
    pub conflicts: Vec<Conflict>,
}

pub fn merge_all_databases(
    local_path: &Path,
    remote_paths: &[PathBuf],
    user_decisions: &HashMap<String, Decision>,
) -> MergeResult;
```

### Phase 3: Conflict Resolution UI (Android)
**New Android files:**
- `ConflictListActivity.kt` — list all unresolved conflicts from the last sync
- `ConflictDetailActivity.kt` — side-by-side comparison view per conflict
- `user_decisions.json` persistence in Kotlin (read/write)

**UI flow:**
1. After each sync, check `conflicts` returned by Rust merge engine
2. If conflicts exist → show notification: "X sync conflicts to resolve"
3. Tap notification → `ConflictListActivity.kt` shows summary
4. Tap individual conflict → `ConflictDetailActivity.kt` shows side-by-side
5. User picks resolution → save to `user_decisions.json` via JNI
6. Decision syncs back to other device via Syncthing

### Phase 4: Unified Timeline Display
**Changes:**
- `Data/SessionModels.kt`: Add `origin_device: String?` field to `AppSession`
- `parser/SessionParser.kt`: Tag events with their source device ID
- `WebUIFragment.kt` / aw-webui integration: Include origin info in query payloads

**Implementation:**
1. When merging events from multiple DBs, store the originating device_id in each event's `data.origin_device` field (AW data format allows arbitrary JSON fields)
2. Modify `androidQuery()` FFI to return merged timeline with origin tags
3. Pass origin info to aw-webui for display (or add a native overlay showing per-device breakdown)

---

## 5. Key Rust Code Locations

| File | Lines | What It Does |
|------|-------|--------------|
| `sync_wrapper.rs` | 15-57 | `pull()` — **THE PROBLEM**: chooses largest .db, discards others |
| `sync_wrapper.rs` | 59-77 | `push()` / `push_with_hostname()` — needs device_id in filename |
| `sync.rs` | 353-506 | `sync_one()` — event-level sync algorithm (can reuse) |
| `sync.rs` | 280-350 | `sync_datastores()` — bucket-level orchestration |
| `accessmethod.rs` | ~66 lines | Trait definition: `get_events`, `insert_events`, `heartbeat` |
| `android.rs` | ~241 lines | JNI bindings — add new functions for multi-DB merge here |

---

## 6. Open Questions & Decisions Needed

1. **What devices should be trusted equally?** Should there be a "primary device" (e.g., home phone) that acts as the source of truth, or is every device equal?
2. **Timeline granularity for conflict detection.** Currently grouping by 1-minute windows — is that fine-grained enough? What about minute-long gaps between devices?
3. **How to display "merged timeline" in aw-webui?** The web UI (Vue.js) renders the timeline but doesn't currently show source device info. Would need either: a) pass origin_device in event data, b) add a native overlay, or c) modify aw-webui.
4. **Battery impact of more frequent sync.** Currently every 15 min. Could increase to every 5 min once Syncthing is handling file-level deduplication (Syncthing won't trigger full transfers if files haven't changed).
5. **What about deletions?** If you uninstall ActivityWatch on one device, should its data be removed from synced timeline? Probably not — but worth deciding now.

---

## 7. Files to Create (New)

### Rust (`aw-server-rust/aw-sync/src/`)
| File | Purpose |
|------|---------|
| `multi_sync.rs` | Merge all device DBs, produce unified result with conflicts flagged |
| `conflict_detector.rs` | Find time windows where two devices have conflicting data |
| `user_decisions.rs` | JSON persistence for user conflict resolutions |

### Android (`mobile/src/main/java/net/activitywatch/android/`)
| File | Purpose |
|------|---------|
| `MultiDeviceMerge.kt` | JNI bridge to Rust merge functions + Kotlin-side decision store |
| `ConflictResolver.kt` | Manages conflict list and user resolution callbacks |
| `user_decisions.json` (in sync folder) | Persistent storage for user conflict decisions (JSON file) |

---

## 8. Risk Assessment

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| Syncthing fails to merge `.db` files correctly (SQLite locking conflicts during transfer) | Medium | Add a file-locking protocol: only sync when aw-server-rust is idle; use Syncthing's "ignore patterns" for temp files |
| Memory OOM on large DBs with multiple devices merged | Low-Medium | Already using chunked batching (BATCH_SIZE = 100); merge also uses chunking |
| User finds conflict UI annoying if too many conflicts | Medium | Smart grouping: show 1 summary screen for all 5-minute window conflicts, not per-event |
| aw-webui doesn't support origin_device display | Low-Medium | Tag events with origin in event `data` field (backward compatible) + native overlay as fallback |

---

## 9. Implementation Status

### Phase 1 — COMPLETE ✅
| File | Change | Purpose |
|------|--------|---------|
| `sync_wrapper.rs` | Added `push_with_hostname_and_device_id()` | Per-device staging: writes to `{sync_dir}/{hostname}/{device_id}_staging/` instead of shared path |
| `sync_wrapper.rs` | Added `pull_all_from_all_hostnames()` | Scans ALL hostname directories for `.db` files, pulls from each (replaces the broken single-hostname pull) |
| `sync_wrapper.rs` | Added `pull_from_hostname()` helper | Iterates device subdirs within a hostname, finds all `.db` files |
| `lib.rs` | Exported new functions | `push_with_hostname_and_device_id`, `pull_all_from_all_hostnames` |
| `android.rs` | Added `syncPushWithDeviceId()` JNI | Receives `device_id` from Kotlin, passes to Rust staging function |
| `android.rs` | Added `syncPullAllFromAllHostnames()` JNI | Pulls from all discovered hostnames (not just one) |
| `SyncInterface.kt` | New `getDeviceId()` | Stable device identifier using installer hash or build fingerprint |
| `SyncInterface.kt` | New `syncBothMultiDeviceAsync()` | Replaces `syncBothAsync`: calls pull-from-all + push-with-device-id |
| `SyncInterface.kt` | New external JNI declarations | `syncPushWithDeviceId`, `syncPullAllFromAllHostnames` |
| `SyncScheduler.kt` | Calls `syncBothMultiDeviceAsync()` | Switches scheduler from old broken sync to Phase 1 |

### What Phase 1 Fixes
- **No more silent data loss**: Each device writes to its own `{hostname}/{device_id}_staging/test.db` path → Syncthing sees them as separate files → no overwrite
- **Pull actually finds other devices**: `pull_all_from_all_hostnames()` scans all hostname dirs in the shared folder, discovers every remote `.db` file, pulls from each
- **Every device sees everyone's data**: After sync completes on Device A, it has pulled Data B's `.db` and synced its events to the local server

### What Phase 1 Does NOT Do (Phase 2+)
- Conflict detection/resolution UI (devices still have separate DBs, no merge yet)
- Unified timeline from all devices (pull brings data in but displays as "synced-from" buckets)
- Origin tagging on events (no `origin_device` field yet)

---

## 10. Priority Order

1. **Phase 1** — Per-device DB writes (safest, most foundational, no merge logic yet)
2. **Phase 2** — Multi-DB merge engine (the core new capability)
3. **Phase 4** — Unified timeline display (can use Phase 2's output, simple integration)
4. **Phase 3** — Conflict resolution UI (depends on Phase 2 conflicts existing)

---

*Last updated: 2026-08-30*
