# 01 — Requirements & Rules

> **This is the requirements document.** Everything here came from the owner. When any other doc
> disagrees with this one, **this one wins** and the other doc gets fixed.
>
> Rules are numbered (`R1`, `R2`, …) so later docs and code comments can cite them.

---

## 1. The product, in one paragraph

ActivityWatch runs on **three or more devices** (Android first, other platforms later). Each device
keeps its own timeline of what happened on it. Above those per-device timelines sits **one combined
timeline** that answers a different question: *not "what did each screen show?" but **"what was I
actually doing?"*** Devices sync peer-to-peer through a Syncthing shared folder — no server, no
account.

---

## 2. Scope rules

| # | Rule |
|---|------|
| **R1** | **Three or more devices**, not two. Nothing may assume a pair. No "primary" device — every device is equal and holds a full copy. |
| **R2** | **Android first**, but no design may block desktop later. Anything Android-specific (SAF, JNI) stays behind an interface. |
| **R3** | **No central server, no account.** Syncthing moves files; the app does all merging locally. |
| **R4** | **Builds happen in GitHub Actions only** — never locally. See [`02_ARCHITECTURE.md` §5](02_ARCHITECTURE.md#5-build-and-release). |

---

## 3. The combined-timeline rules — *the core of this project*

### 3.1 The problem being solved

Two devices, same hour. The phone reports **YouTube playing**. The tablet reports **Kindle, reading**.

- On the **individual** timelines this is fine and correct. Each device honestly reports itself.
- On the **combined** timeline it is wrong twice over:
  1. It claims **two hours of activity inside one hour of wall-clock time**.
  2. It tells the wrong story. The owner was **reading**; YouTube was noise in the background.

> **R5 — The combined timeline answers "what was I doing", not "what were my screens doing."**
> It is an interpretation of the device timelines, never a concatenation of them.

### 3.2 The invariant that makes it meaningful

> **R6 — Wall-clock conservation. This is the most important rule in this document.**
> For any time interval, the combined timeline's total attributed duration **equals the wall-clock
> duration of that interval** — never the sum of the devices' durations.
>
> Consequence: **at any instant, exactly one activity is `foreground`.** Everything else happening
> at that instant is recorded as `background` — kept, visible, attributable, but **contributing zero
> to totals**. Two hours of device activity inside one hour collapses to one hour, with one activity
> foreground and the rest background.

Background time is *not* deleted. It stays queryable ("how much YouTube was playing while I read?").
It simply does not inflate the day.

### 3.3 Contention

> **R7 — A *contention window* is any interval where two or more devices report simultaneous
> non-idle activity.** Contention is detected automatically and never silently resolved.

> **R8 — Contention is shown, not hidden.** In the combined timeline an unresolved contention
> window renders as a distinct **shaded / hatched band**. It is obvious at a glance that this
> stretch of the day is unsettled.

> **R9 — Tap to resolve.** Tapping a shaded band opens the competing activities side by side and the
> owner picks what it really was. That choice is a *decision* (§3.4).

> **R10 — Unresolved is a valid, stable state.** Nothing is lost and nothing breaks while contention
> sits unresolved. Until the owner decides, the window uses the **provisional attribution** (§3.6)
> and stays visibly shaded.

### 3.4 Decisions

A decision resolves one contention window. The available outcomes:

| Outcome | Meaning | Effect on totals |
|---|---|---|
| **`foreground: <device/activity>`** | "This was the real activity." | That activity takes the wall-clock time; the losers become `background`. |
| **`concurrent`** | "I really was doing both." (music while coding) | Still **one** foreground under R6 — the owner picks which one counts; the other is `background` but flagged deliberate, not noise. |
| **`relabel: "<name>"`** | Neither device's label is right; the truth is a new name — *"reading with music on"*. | The custom label takes the wall-clock time. |
| **`ignore`** | Neither counts. Screen was on, owner was not there. | Window contributes **zero**; it becomes idle. |

> **R11 — Decisions are data, never edits.** A decision never rewrites, deletes or mutates a raw
> device event. Raw per-device data stays pristine and the individual timelines never change. The
> combined timeline is *derived* — raw events + decisions, recomputed on demand.
>
> This is what makes decisions safely reversible, safely syncable, and safely re-appliable when a
> device shows up late with backfilled data.

> **R12 — Every decision must be reversible.** Undo restores the shaded, unresolved state.

### 3.5 Manual now, automatic later — and the bridge between them

> **R13 — Ship manual resolution first.** The owner resolves contention by hand. No inference, no
> guessing, no automatic rules in v1.

> **R14 — Every decision records *why it applied*, not just *what was chosen*.** A decision stores
> the full competing-activity signature — which apps, which devices, which categories — not merely
> "window 14:30–14:45 → reading".

> **R15 — The rules engine is nothing more than accumulated decisions.** Because of R14, the stored
> decisions can later be generalised into rules with no data migration and no re-asking.
> **This is why R14 is mandatory from day one** — the v1 format *is* the v2 training data. Getting
> it wrong means asking the owner to re-teach the app everything it already learned.

> **R16 — A decision carries a scope**, chosen at resolution time:
> - **`once`** — this window only.
> - **`always`** — "whenever *phone: YouTube* contends with *tablet: Kindle*, it's reading." This is
>   a user-authored rule, and it is the entire v2 rules engine in embryo.
>
> `always` decisions apply to future contention automatically. They stay visible, listable and
> **individually revocable** — an auto-resolved window is marked as such, so a bad rule is always
> traceable back to the decision that created it and can be undone.

### 3.6 Provisional attribution (before any decision exists)

> **R17 — Unresolved contention still needs a number today.** A window with no decision uses a
> deterministic provisional pick, so the day still totals correctly under R6 while staying shaded
> under R8. The provisional rule is **explicitly a placeholder, not intelligence**: longest-duration
> activity wins, ties broken by a stable device ordering, so **every device computes the identical
> provisional answer**.

> **R18 — Determinism.** Given the same raw events and the same decision set, every device must
> compute a byte-identical combined timeline. No device-local randomness, no "whoever synced last
> wins". Without this, three devices show three different days.

---

## 4. Sync rules

> **R19 — No silent data loss, ever.** No merge step may drop events because a file was smaller,
> older, or arrived second. The current upstream behaviour — *"more than one db found, choosing
> largest"* — is precisely the bug this project exists to remove.

> **R20 — One writer per file.** Syncthing does not merge file *contents*; two writers produce
> `.sync-conflict-*` copies. Therefore **every file in the shared folder is owned and written by
> exactly one device.** All cross-device combining happens at *read* time, by reading everyone's
> files. See [`05_DATA_MODEL.md`](05_DATA_MODEL.md).

> **R21 — Sync must be two-way.** Today the app only ever copies data *out* to the shared folder and
> never reads other devices' data back *in*. This is the single biggest reason nothing works right
> now. See [`03_SYNC.md` §2.1](03_SYNC.md).

> **R22 — Device identity must be genuinely unique per device.** Two phones of the same model,
> installed the same way, must never produce the same ID. See [`03_SYNC.md` §2.3](03_SYNC.md).

> **R23 — Late and offline devices are normal.** A device may be off for a week and then sync. Its
> backfilled events must merge correctly and re-trigger contention detection for the past.

> **R24 — SQLite safety.** Syncthing swaps `.db` files underneath a running process. The app must
> never read or write a database file that Syncthing may replace mid-operation. Imported databases
> are copied into app-private storage before being opened.

---

## 5. Settings & labels sync

> **R25 — Settings sync across devices.** If YouTube is named *"fun"* on one device, it is *"fun"*
> on all of them. Categories, labels, rules and display preferences are shared state, not
> device-local state.

> **R26 — Decisions sync across devices.** A contention resolved on the phone is resolved
> everywhere. The owner never answers the same question twice.

> **R27 — The combined timeline syncs as *inputs*, not as output.** Devices share raw events,
> decisions and settings, then each **recomputes** the combined timeline locally. The rendered
> timeline is never itself transmitted. This is guaranteed to agree by R18, and it self-heals when
> late data arrives (R23).

> **R28 — Some settings stay local.** Anything about *this device* — its display name, sync folder
> path, notification and battery preferences — is device-local and never shared. Only *meaning*
> (labels, categories, rules, decisions) is shared.

> **R29 — Settings conflicts resolve last-write-wins per key**, with the device ID as a
> deterministic tiebreak so all devices converge on the same value (R18).

---

## 6. Mobile UI rules

The bundled web UI (`aw-webui`) was designed for a desktop monitor. On a phone it lays out
horizontally, scrolls sideways, and cuts content off the edge of the screen. It is currently
**painful to use on the device the app actually runs on.**

> **R30 — The app must be usable on a phone, in portrait, with one thumb.** This is a correctness
> requirement, not polish. An unusable UI makes the combined timeline unusable no matter how
> correct the merge is.

> **R31 — The page never scrolls horizontally as a whole.** Content reflows to the screen width.
> Individually wide things — a table, a chart, a long timeline — may scroll sideways *inside their
> own container*, but the page itself must not.

> **R32 — The WebView must honour the page's own viewport, and must allow zoom as an escape
> hatch.** Today it does neither. See [`02_ARCHITECTURE.md` §7](02_ARCHITECTURE.md).

> **R33 — Everything built new is mobile-first.** The combined timeline, the resolution sheet and
> the settings screens are designed at phone width first and adapted upward — never the reverse.
> This work must not inherit the problem it is fixing.

> **R34 — "Functional" comes before "beautiful".** The first target is: nothing cut off, nothing
> unreachable, every control tappable. Visual refinement is a later, separate pass.

---

## 7. What is explicitly *not* in v1

- Automatic contention resolution — R13 defers it, R15 keeps the door open.
- Non-Android platforms — R2 keeps them unblocked.
- Encryption beyond what Syncthing already provides in transit.
- Any central server or account.

---

## 8. Open product questions

Tracked in [`07_OPEN_QUESTIONS.md`](07_OPEN_QUESTIONS.md). The ones that touch these rules:

- **Q1** — ✅ **Resolved (2026-09-02): 60 seconds.** Contention shorter than a minute is absorbed
  into the neighbouring settled segment rather than shaded. Tunable later if it feels wrong in use.
- **Q2** — ✅ **Resolved: no.** `concurrent` may not split time proportionally; R6 forbids it.
- **Q3** — When a device is retired or uninstalled, does its history stay in the combined timeline?
  *Leaning yes — the time really did happen.*
- **Q8** — How far does the mobile UI work go: patch `aw-webui`'s CSS, or build native screens for
  the views that matter most? See [`07`](07_OPEN_QUESTIONS.md).
