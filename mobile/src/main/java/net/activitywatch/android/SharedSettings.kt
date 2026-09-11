package net.activitywatch.android

/**
 * Which settings are shared between devices, and what each sync cycle should do about them
 * ([`05_DATA_MODEL.md`] §5, **R25/R28/R29**).
 *
 * Pure -- no Android, no I/O, no clock -- so the whole decision is unit-testable. `SyncInterface`
 * supplies the three inputs and carries out the plan.
 */

/**
 * The settings that mean the same thing on every device, and are therefore shared.
 *
 * **An allowlist, never a denylist.** **R28** says a setting about *this device* must never leave
 * it, and the two lists grow at different speeds: aw-webui gains new keys on its own schedule, and
 * a device-local one that leaked by default would be discovered only after it had already
 * overwritten another device's copy. Anything unlisted stays put.
 *
 * The names are aw-webui's actual settings keys, not the `category.<app>` shape
 * [`05_DATA_MODEL.md`] §5 sketched -- see [SHARED_SETTING_PREFIXES] for why both exist.
 */
internal val SHARED_SETTING_KEYS: Set<String> = setOf(
    // What counts as what. Renaming YouTube to "fun" edits `classes`; it is the key 2.3's check
    // is really about.
    "classes",
    "category_sets",
    "active_set_ids",
    // Which category an activity is, when two equally deep rules both match it and the
    // owner has said which one wins (roadmap 4.4e). An answer about what something *is*,
    // so it means the same on every device -- and a pin that lived on only one device
    // would make two devices categorise the same day differently.
    "category_pins",
    // What is deliberately not counted, and what is always counted -- meaning, same as above.
    "privacy_filters",
    "always_active_pattern",
    // Where a day and a week begin. Shared because they change what a day's totals *mean*: two
    // devices disagreeing here disagree about which day an evening's activity belongs to, and the
    // combined timeline (Phase 3) would be summing across a boundary only one of them believes in.
    "startOfDay",
    "startOfWeek",
    "durationDefault",
)

/**
 * Key prefixes that are shared as well as [SHARED_SETTING_KEYS].
 *
 * [`05_DATA_MODEL.md`] §5 named `category.*`, `label.*` and `rule.*`. Nothing writes those today --
 * aw-webui keeps the whole categorisation in one `classes` value instead -- but they are this
 * project's own namespace for Phase 4's rules, so the routing understands them from the start
 * rather than being retrofitted later.
 */
internal val SHARED_SETTING_PREFIXES: List<String> = listOf("category.", "label.", "rule.")

/**
 * Keys that look shareable and are not, listed so the reasoning survives someone reading the
 * allowlist and wondering what was left out. Documentation only -- [isSharedSettingKey] rejects
 * everything unlisted anyway.
 *
 * - `theme`, `landingpage`, `locale`, `useColorFallback` -- how *this* screen looks (**R28**). A
 *   phone in a pocket and a tablet on a desk may reasonably differ.
 * - `views`, `saved_queries` -- they name buckets, and bucket ids carry the hostname that produced
 *   them. Copied across, they would point at buckets that do not exist on the receiving device.
 * - `devmode`, `requestTimeout`, `hideUnsupportedVisualizations`, `showYearly`, `useMultidevice` --
 *   per-device toggles, several of them about what this device's hardware can cope with.
 * - `newReleaseCheckData`, `userSatisfactionPollData`, `uncategorizedNotificationData` -- schedules
 *   for prompts on this device; sharing them would fire the same prompt on all of them at once.
 */
internal val DELIBERATELY_LOCAL_SETTING_KEYS: Set<String> = setOf(
    "theme", "landingpage", "locale", "useColorFallback",
    "views", "saved_queries",
    "devmode", "requestTimeout", "hideUnsupportedVisualizations", "showYearly", "useMultidevice",
    "newReleaseCheckData", "userSatisfactionPollData", "uncategorizedNotificationData",
)

internal fun isSharedSettingKey(key: String): Boolean =
    key in SHARED_SETTING_KEYS || SHARED_SETTING_PREFIXES.any { key.startsWith(it) }

/**
 * What one cycle should do about settings.
 *
 * @property linesToAppend new lines for *our own* `settings.jsonl` (**R20**) -- the local edits we
 *   are publishing.
 * @property valuesToApply settings to write into this device's datastore, because another device's
 *   value won.
 * @property applied what to remember as agreed, for the next cycle's [planSettingsSync].
 */
internal data class SettingsPlan(
    val linesToAppend: List<SharedRecord.Setting>,
    val valuesToApply: Map<String, String>,
    val applied: Map<String, String>,
) {
    val isEmpty: Boolean get() = linesToAppend.isEmpty() && valuesToApply.isEmpty()
}

/**
 * Decide what to publish and what to accept, given what is on this device and what every device's
 * `settings.jsonl` says.
 *
 * @param local this device's stored settings, values as the raw JSON bodies the datastore holds.
 * @param merged the winning line per key across all devices ([effectiveSettings]).
 * @param applied what this function concluded last time -- **the piece that makes the difference**
 *   between "the owner edited this here" and "a peer edited it and we have not applied it yet".
 *   Both look like `local != merged`; only a comparison against what we last agreed to tells them
 *   apart. It is device-local state and lives in `AWPreferences` (**R28**).
 * @param now the timestamp for lines we publish.
 * @param deviceUuid this device, as `updated_by`.
 *
 * The rule per key is two lines long:
 *
 * - **`local != applied`** -- the owner changed it *here*. Publish it. Ours is the newest line, so
 *   it wins the merge on every device, including the ones that changed it too but earlier.
 * - **otherwise** -- we have no news. Take whatever the merge says, and write it locally if it
 *   differs from what we hold.
 *
 * Two devices editing the same key between one sync and the next both publish; **R29**'s
 * last-write-wins with the lowest-uuid tiebreak picks one, and the loser's next cycle finds
 * `local == applied` and accepts the winner. Convergence takes one extra cycle and no coordination.
 *
 * A key absent from [local] is a key the owner never saved -- aw-webui stores nothing until it is
 * changed. It is therefore never published (publishing "absent" would mean publishing this build's
 * defaults over a peer's real choice), but it *is* accepted from a peer, which is what makes a
 * fresh device pick up an established one's categories.
 */
internal fun planSettingsSync(
    local: Map<String, String>,
    merged: Map<String, SharedRecord.Setting>,
    applied: Map<String, String>,
    now: String,
    deviceUuid: String,
): SettingsPlan {
    val lines = mutableListOf<SharedRecord.Setting>()
    val toApply = LinkedHashMap<String, String>()
    val nextApplied = LinkedHashMap<String, String>()

    val keys = (local.keys + merged.keys + applied.keys).filter { isSharedSettingKey(it) }.sorted()
    for (key in keys) {
        val localValue = local[key]
        val mergedValue = merged[key]?.value

        if (localValue != null && localValue != applied[key]) {
            // Changed here since we last agreed -- publish it and keep it.
            lines += SharedRecord.Setting(key, localValue, now, deviceUuid)
            nextApplied[key] = localValue
            continue
        }
        if (mergedValue != null) {
            if (mergedValue != localValue) toApply[key] = mergedValue
            nextApplied[key] = mergedValue
            continue
        }
        // No line anywhere and nothing new here: a key only we hold, already published. Keep
        // remembering it so a later edit is still recognisable as one.
        if (localValue != null) nextApplied[key] = localValue
    }
    return SettingsPlan(lines, toApply, nextApplied)
}
