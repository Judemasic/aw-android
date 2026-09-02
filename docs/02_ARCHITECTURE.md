# 02 — Architecture

> What exists today, where the code lives, and how a build is produced.
> For what is *broken* in it, see [`03_SYNC.md`](03_SYNC.md).

---

## 1. The two repositories

Both are forks. Work happens on the **`beta`** branch of each.

| Repo | Fork | Role |
|---|---|---|
| **aw-android** | `github.com/Judemasic/aw-android` | The Android app. Kotlin. Contains the other repo as a **git submodule** at `./aw-server-rust`. |
| **aw-server-rust** | `github.com/Judemasic/aw-server-rust` | The embedded server + sync engine. Rust, compiled to `.so` and loaded over JNI. |

> ⚠️ **The submodule pointer is load-bearing.** `aw-android/.gitmodules` must point at the
> **fork's `beta`**, not at upstream ActivityWatch. If it points upstream, CI silently builds
> native libraries without any of our changes and the app fails at runtime with
> `UnsatisfiedLinkError`. This has already cost one full debugging cycle — see
> [`06_ROADMAP.md` §Progress log](06_ROADMAP.md).

Local checkouts:

```
c:\dev\New folder\
  aw-android\              ← app (branch: beta)
    aw-server-rust\        ← submodule checkout
  aw-server-rust\          ← standalone checkout of the Rust fork
  aw-server-rust-old\      ← stale, not used
  activitywatch\           ← upstream reference checkout
```

---

## 2. Runtime shape on one device

```
┌─ Android app process ──────────────────────────────────────────┐
│                                                                │
│  UsageStatsManager ──► SessionParser ──► RustInterface (JNI)    │
│  AccessibilitySvc  ──►                          │              │
│                                                 ▼              │
│                                        libaw_server.so         │
│                                        (aw-server-rust)        │
│                                        HTTP on localhost:5600  │
│                                                 │              │
│                                                 ▼              │
│                                    app-private SQLite          │
│                                       files/data/test.db       │
│                                                 ▲              │
│  WebUIFragment (WebView → localhost:5600) ──────┘              │
│                                                                │
│  SyncScheduler ──► SyncInterface (JNI) ──► libaw_sync.so       │
│                          │                                     │
│                          └──► SAF mirror ──► shared folder     │
└────────────────────────────────────────────────────────────────┘
                                       │
                              Syncthing (separate app)
                                       │
                              other devices
```

Two **separate** native libraries are loaded — `libaw_server.so` and `libaw_sync.so`. They are
distinct cdylibs and **do not share process state**, which is why environment variables
(`AW_SYNC_DIR`, `XDG_*`) have to be set independently for each. This trips people up regularly.

---

## 3. Key files

### Android — `mobile/src/main/java/net/activitywatch/android/`

| File | Role |
|---|---|
| `RustInterface.kt` | JNI bridge to `libaw_server.so` — start server, insert events, run queries. |
| `SyncInterface.kt` | JNI bridge to `libaw_sync.so` + the SAF mirror. **Where the sync bugs live.** |
| `SyncScheduler.kt` | 15-minute sync timer (Handler chain + AlarmManager fallback). |
| `SyncWorker.kt` | WorkManager worker wrapping a sync run. |
| `SyncSettingsActivity.kt` | Enable sync, pick the shared folder via SAF. |
| `BackgroundService.kt` | Foreground service; owns collection and hostname migration. |
| `AWPreferences.kt` | SharedPreferences wrapper. Device-local settings only (R28). |
| `data/SessionModels.kt` | `AppSession` and friends — no device origin field yet. |
| `parser/SessionParser.kt` | Turns raw usage events into sessions. |
| `fragments/WebUIFragment.kt` | Hosts aw-webui in a WebView. |

### Rust — `aw-server-rust/aw-sync/src/`

| File | Role |
|---|---|
| `sync_wrapper.rs` | Public entry points: `pull`, `push`, `push_with_hostname_and_device_id`, `pull_all_from_all_hostnames`. |
| `sync.rs` | The engine — `sync_run`, `sync_datastores`, `sync_one`, `setup_local_remote`. |
| `util.rs` | Remote discovery — `get_remotes`, `find_remotes`, `find_remotes_nonlocal`. |
| `dirs.rs` | `get_sync_dir()` — reads the `AW_SYNC_DIR` env var. |
| `accessmethod.rs` | `AccessMethod` trait unifying local datastore and HTTP client. |
| `android.rs` | JNI exports, `catch_unwind` wrappers, `android_logger` init. |

---

## 4. Storage locations on Android

| What | Path | Who can see it |
|---|---|---|
| Live datastore | `files/data/test.db` (app-private) | App only. |
| `AW_SYNC_DIR` | `getExternalFilesDir(null)/sync` | **App only** — on Android 11+ other apps cannot read this. |
| Shared folder | User-chosen via SAF, e.g. `/storage/emulated/0/ActivityWatch-sync` | Syncthing and other apps. |

> **This split is the whole reason a mirror step exists.** The Rust sync engine can only work
> against a normal filesystem path, and the only normal path it is allowed to write is app-private.
> Syncthing can only see the SAF folder. Something has to move bytes between them — and today that
> something only moves them in one direction. See [`03_SYNC.md`](03_SYNC.md).

---

## 5. Build and release

> **R4 — GitHub Actions only. Do not attempt a local build.** It would require Rust, the Android
> NDK and the Android SDK on Windows; that toolchain is deliberately not set up.

1. Commit and push Rust changes to `Judemasic/aw-server-rust@beta` **first**.
2. In `aw-android`, update the submodule pointer and commit it.
3. Push `aw-android@beta`.
4. Run the **Build** workflow: <https://github.com/Judemasic/aw-android/actions/workflows/build.yml>
5. Download the `aw-android-apk` artifact from the run and install on each device.

> ⚠️ **CI cache trap.** `build.yml` keys its native-library cache on
> `.git/modules/aw-server-rust/HEAD`. If Rust changes exist only as uncommitted working-tree edits,
> the key does not change, CI gets a **cache hit**, skips the Rust build, and ships an APK with
> **stale `.so` files**. The symptom is `UnsatisfiedLinkError` for a function you can plainly see in
> the source. **Always commit and push the Rust side before building.**

### 5.1 Local type-checks *(added 2026-09-02)*

**R4 said "do not attempt a local build" because no toolchain was set up. That premise was
partly wrong** — the machine already had Android Studio's JDK 21, the SDK with platform 36, MSVC
14.44, and **NDK `28.2.13676358`, the exact version `build.yml` pins**. Only Rust was missing.

R4 still holds for **building an APK**. It does *not* hold for **type-checking**, which needs no
NDK and no linking. Run `scripts/check-local.sh`:

| Check | Command | Time | Covers |
|---|---|---|---|
| Kotlin | `./gradlew :mobile:compileDebugKotlin` | ~70s | all Kotlin |
| Rust | `cargo check -p aw-sync --lib` | ~25s | `sync_wrapper.rs`, `dirs.rs`, `util.rs`, `sync.rs` |

Kotlin needs no Rust at all: `cargoBuild` is deliberately **not** wired into the Gradle build
(`mobile/build.gradle`), so Kotlin compiles without any `.so` present.

> ⚠️ **`android.rs` is NOT covered.** It sits behind `#[cfg(target_os = "android")]`
> (`aw-sync/src/lib.rs`), so the host check **skips it silently** — a green local run says nothing
> about the JNI layer, which is where steps 1.0 and 1.1 live.
>
> Checking it needs `--target aarch64-linux-android`, which is currently **not possible on
> Windows**. `aw-server/Cargo.toml` pulls `openssl-sys` with `vendored` for the android target, so
> the check must build OpenSSL 3.5.1 from source, and that needs a Unix-shaped perl:
> Strawberry perl has the modules but `Configure` rejects it (*"doesn't produce Unix like paths"*);
> Git Bash's perl produces Unix paths but is a stripped build missing core modules. Supplying the
> missing pure-perl modules gets `Configure` to pass completely; `make` then dies on OpenSSL's
> `$(CROSS_COMPILE)$(CC)` joining without a separator (`.../bin` + `clang.exe` → `binclang.exe`).
>
> Investigated 2026-09-02 and **stopped there deliberately** — `mobile/build.gradle` carries the
> upstream maintainers' own note, *"Doesn't work, chokes on building openssl-sys."* CI covers
> `android.rs`, and CI has to run before device testing regardless.

**These checks prove compilation, nothing more.** They do not link, produce `.so` files, build an
APK, or show that sync works. Only two real devices do that (roadmap 1.5).

---
## 7. The mobile UI problem

> **Symptom:** the UI is laid out for a PC. It scrolls left and right, and a lot of it is cut off
> the edge of the screen. *(R30–R34)*

There are **two layers** to this, and they are worth separating because one is nearly free.

### 7.1 Layer 1 — the WebView is misconfigured *(cheap, do first)*

`aw-webui` already ships a correct mobile viewport declaration (`aw-webui/index.html:7`):

```html
<meta name="viewport" content="width=device-width,initial-scale=1.0">
```

But `WebUIFragment.onCreateView` sets only two settings:

```kotlin
myWebView.settings.javaScriptEnabled = true
myWebView.settings.domStorageEnabled = true
```

`WebSettings.useWideViewPort` defaults to **`false`**, which means **the WebView ignores that
`<meta viewport>` tag entirely.** The page's own mobile awareness is being thrown away before it is
ever used.

Worse, zoom is not enabled either (`setSupportZoom`, `builtInZoomControls`), so when something *is*
cut off, there is no way to pinch out and reach it. The content is not just badly laid out — it is
**unreachable**, which is what makes this a correctness issue under R30 rather than a cosmetic one.

**The fix is a handful of lines** — honour the viewport, enable zoom without the on-screen zoom
buttons, and let the layout use the full width. This is expected to recover a meaningful share of
the problem on its own, and it costs almost nothing to try, which is why it is sequenced early
(Roadmap 1.6) rather than saved for the UI phase.

### 7.2 Layer 2 — `aw-webui` is desktop-first *(the real work)*

Even with a correct viewport, the underlying app is built for a monitor: a fixed navbar, wide data
tables, and timeline/Vega charts sized in absolute pixels. These overflow at phone widths regardless
of viewport handling, and that is what produces the sideways scroll (**R31**).

Options are compared as **Q8** in [`07_OPEN_QUESTIONS.md`](07_OPEN_QUESTIONS.md); the work is
Phase 5 in [`06_ROADMAP.md`](06_ROADMAP.md).

> This also feeds **Q4** (native vs `aw-webui` for the combined timeline). If `aw-webui` needs
> substantial mobile work regardless, that shifts the balance toward doing the new combined-timeline
> screens natively and mobile-first (**R33**) instead of adding to a desktop-shaped codebase.

---

## 8. What does not exist yet

Everything in [`01_REQUIREMENTS_AND_RULES.md` §3](01_REQUIREMENTS_AND_RULES.md) — the combined
timeline. Concretely, there is today:

- no device-origin tag on events,
- no contention detection,
- no decision store,
- no combined-timeline view,
- no shared settings,
- no working cross-device sync to build any of it on,
- and no usable mobile layout to show any of it in (§7).

The build order that fixes this is in [`06_ROADMAP.md`](06_ROADMAP.md).
