# 05 — Data Model & Shared State

> The exact contents of the shared folder. This is the **cross-device contract**: every device
> reads and writes these formats, so a change here is a compatibility break and needs a version bump.

---

## 1. Governing constraint

Syncthing synchronises **files**, and resolves a two-writer collision by keeping both as
`.sync-conflict-*` copies. It never merges file contents.

> **R20 — one writer per file.** Every path below is owned by exactly one device. Nothing merges
> files in place; devices merge by *reading all of them*.

Two structural consequences:

1. **Mutable state is append-only.** Never rewrite a line. Corrections and deletions are new lines
   (tombstones). A rewrite is a whole-file replacement, which is exactly what generates conflict
   copies.
2. **Order is never trusted.** Files arrive in any order, arbitrarily late (**R23**). Merge results
   must depend only on content, never on arrival order (**R18**).

---

## 2. Layout

```
<shared folder>/
  VERSION                          ← schema version, e.g. "1"
  devices/
    <device_uuid>/
      meta.json                    ← replaced wholesale by its owner only
      events.db                    ← SQLite snapshot, owner only
      decisions.jsonl              ← append-only
      settings.jsonl               ← append-only
```

`<device_uuid>` is the persisted `UUID.randomUUID()` from
[`03_SYNC.md` §2.3](03_SYNC.md) — unique by construction (**R22**).

---

## 3. `meta.json`

Identity and capabilities of one device. Small, and rewritten wholesale — safe, because its owner is
its only writer.

```jsonc
{
  "device_uuid": "9f2c1e84-3b7a-4c55-8d21-6ae0f5b91c73",
  "display_name": "Jude's phone",     // shared (R25) — the label peers show
  "role": "phone",                     // phone | tablet | laptop | desktop
  "platform": "android",
  "app_version": "0.12.3",
  "last_seen": "2026-09-02T18:41:07Z",
  "schema_version": 1
}
```

> `role` is the match key for decision signatures ([`04`](04_COMBINED_TIMELINE.md) §3) — it is what
> lets a rule survive replacing a phone. `display_name` is shared so all devices label the timeline
> identically; the *sync folder path* and other device-local settings are **not** here (**R28**).

---

## 4. `decisions.jsonl`

One JSON object per line, append-only. Records a contention resolution (**R11** — data, never an
edit).

```jsonc
{
  "id": "d_01J9X2K...",                      // ULID — sortable, collision-free across devices
  "type": "decision",
  "created_at": "2026-09-02T14:47:11Z",
  "created_by": "9f2c1e84-…",                // authoring device
  "window": { "start": "2026-09-02T14:30:00Z", "end": "2026-09-02T14:45:00Z" },
  "signature": {                              // R14 — why it applied, not just what
    "participants": [
      { "device_role": "phone",  "device_uuid": "9f2c…", "app": "com.google.android.youtube", "category": "video" },
      { "device_role": "tablet", "device_uuid": "3a71…", "app": "com.amazon.kindle",          "category": "reading" }
    ]
  },
  "resolution": {
    "outcome": "foreground",                  // foreground | relabel | ignore
    "foreground": { "device_role": "tablet", "app": "com.amazon.kindle" },
    "label": null,                            // set when outcome = relabel
    "deliberate_background": ["com.google.android.youtube"]   // marks `concurrent` intent
  },
  "scope": "always"                           // once | always   (R16)
}
```

### 4.1 Tombstones (undo — R12)

```jsonc
{ "id": "t_01J9X…", "type": "tombstone", "created_at": "…", "created_by": "…", "revokes": "d_01J9X2K…" }
```

A tombstone may live in a **different** device's file than the decision it revokes — that is normal
and is exactly why one-writer-per-file plus read-time merging is the right shape. Revocation is
resolved during the merge, not on disk.

### 4.2 Merge algorithm (deterministic — R18)

1. Read every `decisions.jsonl` from every device; concatenate.
2. Drop any decision whose `id` is revoked by a tombstone.
3. Group the rest by `(window, signature)`.
4. Within a group, keep the one with the highest `created_at`; **ties break on lowest
   `created_by` UUID.**

Step 4's tiebreak is what guarantees three devices reach the same answer without coordination.
"Most recently synced" would not, and must not be used.

---

## 5. `settings.jsonl`

Shared *meaning* — labels, categories, rules (**R25**). Append-only key/value log; last write wins
per key (**R29**).

```jsonc
{ "type": "setting", "key": "category.com.google.android.youtube", "value": "fun",
  "updated_at": "2026-09-01T09:12:00Z", "updated_by": "9f2c…" }
```

Effective value per key: highest `updated_at`, ties broken by lowest `updated_by` (**R18/R29**) —
the same rule as §4.2, deliberately.

**Shared keys** (`category.*`, `label.*`, `rule.*`, display preferences that describe meaning).
**Never shared** (**R28**): sync folder path, notification settings, battery/scheduling options,
per-device toggles. These stay in `AWPreferences`.

> **Compaction:** the log grows without bound. When it exceeds a threshold, its owner — and only its
> owner (**R20**) — may rewrite *its own* file to the effective values it contributed, preserving
> `updated_at`. Safe precisely because no other device writes that file.

---

## 6. `events.db`

The device's datastore snapshot, produced by aw-sync's push. Owner-written only, per **R20**.

Peers **copy it into app-private storage before opening it** (**R24**) — never open it in place in
the SAF folder, where Syncthing may replace it mid-read.

Events gain an origin tag during the merge so the combined timeline can attribute them. Origin is
recorded at **merge** time from the containing directory rather than being written into each event
at capture time, which keeps raw per-device data untouched (**R11**) and costs nothing at write.

---

## 7. Local-only state

Stays in `AWPreferences`, never in the shared folder:

| Key | Why local |
|---|---|
| `device_uuid` | Identity — generated once, must never be copied to another device. |
| `sync_dir_uri` | A SAF URI, meaningless on any other device. |
| `sync_enabled` | Per-device choice. |
| notification / battery prefs | Per-device (**R28**). |

> `device_uuid` being local-only is load-bearing. Restoring an app backup onto a second device would
> otherwise clone the UUID and recreate Blocker 3 ([`03_SYNC.md` §2.3](03_SYNC.md)) with two devices
> writing one directory. **Guard:** on first run after a restore, if `meta.json` for our UUID exists
> in the shared folder and reports a different `platform`/`app_version` lineage than ours, mint a new
> UUID.

---

## 8. Versioning

`VERSION` at the shared-folder root holds the schema version.

- A device reading a **higher** version must refuse to merge and warn, rather than silently
  misreading. Silent misreads across devices are unrecoverable; a refusal is not.
- Unknown JSONL line types are **ignored, not dropped** — never rewritten away by compaction, so an
  older device cannot destroy a newer device's data it does not understand.
