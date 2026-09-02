# ActivityWatch Multi-Device — Plan Index

> ActivityWatch on **three or more devices**, each keeping its own honest timeline, with **one
> combined timeline above them** that answers a different question: *not "what were my screens
> doing?" but* **"what was I actually doing?"**
>
> **The one job:** YouTube is playing on the phone while you read on the tablet. The per-device
> timelines are both right. The combined timeline must say **reading** — and must never claim two
> hours happened inside one.

This `docs/` folder is the **single source of truth** for the design, written so work can resume in
a fresh conversation. Read this file first, then open [`06_ROADMAP.md`](06_ROADMAP.md) — its
**👉 START HERE** block names the next step.

---

## Current state, in one line

> **Sync works one-way, for the first time.** Six blockers were found
> ([`03_SYNC.md`](03_SYNC.md)); **five are fixed and verified on hardware**. A device now writes
> `<sync>/<hostname>/<device_id>/test.db` and the SAF mirror copies it into the Syncthing folder —
> the original symptom is gone.
>
> **What remains is Blocker 1, the architectural one:** the mirror is **export-only**, so a device
> still cannot *read* another device's data. That is step **1.4**, and it is now the single thing
> between here and real multi-device sync.

---

## The documents

| # | Doc | What it is for |
|---|---|---|
| **01** | [Requirements & Rules](01_REQUIREMENTS_AND_RULES.md) | **The rules (R1–R29).** What the product must do. Everything else defers to this. |
| **02** | [Architecture](02_ARCHITECTURE.md) | The two repos, runtime shape, key files, how to build. |
| **03** | [Sync: Diagnosis & Design](03_SYNC.md) | Why sync is broken today and the design that replaces it. |
| **04** | [The Combined Timeline](04_COMBINED_TIMELINE.md) | Contention, decisions, the resolution UI, the computation pipeline. |
| — | *Mobile UI problem* | Not its own doc — root cause in [`02` §7](02_ARCHITECTURE.md), rules R30–R34 in [`01` §6](01_REQUIREMENTS_AND_RULES.md), work in [`06` Phase 5](06_ROADMAP.md). |
| **05** | [Data Model & Shared State](05_DATA_MODEL.md) | Exact shared-folder formats — the cross-device contract. |
| **06** | [Roadmap](06_ROADMAP.md) | Phased plan with per-step checks, plus the progress log. |
| **07** | [Open Questions](07_OPEN_QUESTIONS.md) | What is still undecided, and what each one blocks. |

---

## How to use these docs across conversations

1. **Skim the Decision Log below** so settled choices are not re-litigated.
2. **Open [`06_ROADMAP.md`](06_ROADMAP.md)**, find the first unticked step in the current phase.
3. **Do one step, run its check, stop.** Steps are sized to fit one conversation.
4. **Before stopping, update the step in place** — mark it `✅ DONE (date)`, write a **Result**
   describing what is *actually true now*, and flag with ⚠️ anything not verified. Then append to
   the Progress Log.
5. **If a decision changes**, update the relevant doc *and* add a row to the Decision Log. Supersede
   rows; never delete them.

**A step is not finished when the code compiles. It is finished when someone who wasn't there could
take it from here.**

---

## Decision log

| # | Decision | Why | Where |
|---|---|---|---|
| **D1** | Combined timeline answers *"what was I doing"*, not *"what were my screens doing"* | The whole point of merging; a concatenation would be worse than useless | [`01`](01_REQUIREMENTS_AND_RULES.md) R5 |
| **D2** | **Wall-clock conservation** — exactly one foreground activity per instant; everything else is background and counts zero | Without it the merged day inflates past 24 hours and every total becomes meaningless | [`01`](01_REQUIREMENTS_AND_RULES.md) R6 |
| **D3** | Unresolved contention is shaded, tappable, and stable | The owner's own description; and it must be safe to leave unanswered indefinitely | [`01`](01_REQUIREMENTS_AND_RULES.md) R8–R10 |
| **D4** | Decisions are **data layered over raw events**, never edits to them | Keeps per-device timelines pristine, makes undo trivial, and lets late-arriving data be re-merged | [`01`](01_REQUIREMENTS_AND_RULES.md) R11 |
| **D5** | Manual resolution ships first; **every decision stores its signature anyway** | The rules engine is just accumulated decisions — storing signatures from day one means v1 data becomes v2 rules with no migration and no re-asking | [`01`](01_REQUIREMENTS_AND_RULES.md) R14–R16 |
| **D6** | **One writer per file** in the shared folder; append-only logs; merge at read time | Syncthing replaces files, never merges them — two writers means permanent `.sync-conflict-*` copies. Supersedes the earlier single shared `user_decisions.json` | [`05`](05_DATA_MODEL.md) §1 |
| **D7** | Every merge is **deterministic**, with UUID tiebreaks | Three devices must compute the same day without coordinating; any clock- or arrival-order-based rule breaks this | [`01`](01_REQUIREMENTS_AND_RULES.md) R18 |
| **D8** | Device ID is a **persisted random UUID** | Installer package and `Build.FINGERPRINT` are identical across same-model, same-install devices — the current code gives both phones the same ID | [`03`](03_SYNC.md) §2.3 |
| **D9** | Imported databases are **copied to app-private storage before being opened** | Syncthing swaps files mid-read; this also removes the need for the previously planned datastore close/reopen API | [`03`](03_SYNC.md) §3.3 |
| **D10** | Drop the `_staging` directory level | It *created* the push/pull depth mismatch that makes pull a silent no-op | [`03`](03_SYNC.md) §2.2 |
| **D11** | Settings that carry **meaning** sync; settings about **this device** stay local | "YouTube = fun" must be shared; a SAF URI is meaningless elsewhere | [`01`](01_REQUIREMENTS_AND_RULES.md) R25/R28 |
| **D12** | Combined timeline syncs as **inputs**, never as rendered output | Each device recomputes; guaranteed to agree by D7 and self-heals when a device syncs late | [`01`](01_REQUIREMENTS_AND_RULES.md) R27 |
| **D13** | The computation pipeline lives in **Rust**, not Kotlin | Keeps the native-vs-aw-webui renderer choice (Q4) cheap to reverse and unblocks desktop later | [`04`](04_COMBINED_TIMELINE.md) §5 |
| **D14** | Builds run in **GitHub Actions only** | The local Windows machine has no Rust/NDK/SDK toolchain, deliberately | [`02`](02_ARCHITECTURE.md) §5 |
| **D15** | Contention under **60 seconds** is ignored, not shaded | Walking between devices makes seconds-long overlaps; shading them all would train the owner to ignore shading entirely. Owner's call, exposed as a setting | [`07`](07_OPEN_QUESTIONS.md) Q1 |
| **D16** | A usable phone UI is a **correctness requirement**, not polish | Content is currently cut off *and* unreachable (zoom is disabled) — an unusable UI makes a correct merge worthless | [`01`](01_REQUIREMENTS_AND_RULES.md) R30–R34 |
| **D17** | The WebView viewport fix is sequenced **early (1.6)**, not in the UI phase | Handful of lines, independent of sync, makes two-device verification easier — and measures how much of the UI problem is anything else | [`02`](02_ARCHITECTURE.md) §7.1 |
| **D18** | Device identity is **aw-server's existing UUID**, read over JNI — not a second one minted in Kotlin | `setup_local_remote` already names the device directory from `info.device_id`, and `aw-server/src/device_id.rs` already persists a UUID v4. **Supersedes the Kotlin half of D8**, whose intent it satisfies. A locally minted UUID would have needed a permanent mapping to the server's, and Phase 2's `devices/<uuid>/` would key on a different value than the `.db` directory beside it | [`03`](03_SYNC.md) §2.3 |
| **D19** | Upstream fixes are **ported by hand, never cherry-picked**, when they touch `aw-sync/` or `SyncInterface.kt` | `aw-android#249` fixes a bug we have, but its Kotlin declares a `setDataDir` JNI symbol this fork does not export → `UnsatisfiedLinkError`. Their call sites do not always exist here | [`06`](06_ROADMAP.md) Upstream maintenance |
| **D20** | The blocker list is **open until 1.5 is green**, not closed at five | Four were found by reading source; the fifth — and the most decisive — was found only by reading *upstream's* commits. Reasoning from source has already missed one | [`03`](03_SYNC.md) §2.5 |
| **D21** | **Type-check locally, build in CI.** Kotlin + host-target Rust run on this machine; APKs stay in Actions | The compile-error round-trip was the estimate's dominant cost and it is now ~95s instead of ~30min. Building the APK locally is a different proposition — cross-compiling OpenSSL for Android from Windows fails, as upstream's own `build.gradle` note says. **Qualifies R4/D14 rather than overturning it** | [`02`](02_ARCHITECTURE.md) §5.1 |
| **D22** | `android.rs` is **CI-verified only**, and a green local check must never be read as covering it | It is `#[cfg(target_os = "android")]`, so the host check skips it *silently* — the dangerous kind of gap. The JNI layer is where 1.0 and 1.1 live | [`02`](02_ARCHITECTURE.md) §5.1 |
| **D23** | **Commit a fixed debug keystore.** Debug builds sign with `mobile/debug.keystore`, not the build machine's generated one | CI runners are ephemeral, so every build was signed differently and every device install demanded an uninstall — wiping the history this project exists to preserve. A debug keystore is not a secret (default password, self-signed, `.debug` suffix) and cannot touch the release app | [`02`](02_ARCHITECTURE.md) §5.2 |
| **D24** | **Cross-check JNI symbol names locally** — every Kotlin `external fun` must have a matching `Java_..._<name>` Rust export | The only bug that actually stopped sync was a name mismatch, and no compiler on either side can see it: the Rust builds, the `.so` loads, the symbol is just absent under the name the JVM wants. It cost two days of looking at the wrong layer | [`03`](03_SYNC.md) §2.6 |

---

## Repos

| Repo | Branch | Role |
|---|---|---|
| [Judemasic/aw-android](https://github.com/Judemasic/aw-android) | `beta` | The app. Contains the Rust repo as a submodule. |
| [Judemasic/aw-server-rust](https://github.com/Judemasic/aw-server-rust) | `beta` | Embedded server + sync engine. |

Build: [Actions → Build](https://github.com/Judemasic/aw-android/actions/workflows/build.yml).
**Push the Rust side first** — see the CI cache trap in [`02`](02_ARCHITECTURE.md) §5.
