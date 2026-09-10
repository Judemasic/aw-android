package net.activitywatch.android

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlin.random.Random

/**
 * The append-only shared logs: `decisions.jsonl` and `settings.jsonl`
 * ([`05_DATA_MODEL.md`] §4 and §5).
 *
 * Everything in this file is pure -- no Android, no I/O -- so the merge can be unit-tested on the
 * JVM. [SharedFolder] owns the SAF side (reading every device's copy, appending to our own).
 *
 * Two rules from [`05_DATA_MODEL.md`] §1 shape all of it:
 *
 * - **One writer per file (R20).** A device only ever appends to its own
 *   `devices/<our uuid>/` files. Nothing here rewrites a peer's file, and nothing merges files in
 *   place -- devices merge by *reading all of them*.
 * - **Order is never trusted (R18/R23).** Files arrive in any order, arbitrarily late. Every
 *   function below depends on line *content* only; feeding the same lines in a different order
 *   must produce an identical result, which `SharedStoreTest` asserts against shuffled input.
 */

internal const val SHARED_DECISIONS_FILE = "decisions.jsonl"
internal const val SHARED_SETTINGS_FILE = "settings.jsonl"

/** Line `type` values this build understands. Anything else is a [SharedRecord.Unknown]. */
internal const val RECORD_TYPE_DECISION = "decision"
internal const val RECORD_TYPE_TOMBSTONE = "tombstone"
internal const val RECORD_TYPE_SETTING = "setting"

internal const val SCOPE_ONCE = "once"
internal const val SCOPE_ALWAYS = "always"

/**
 * One line of a shared JSONL file.
 *
 * [Unknown] is not a parse failure, it is a *feature*: [`05_DATA_MODEL.md`] §8 requires that a line
 * type we do not understand is ignored rather than dropped, so a compaction written by an older
 * build cannot destroy a newer build's data. Keeping [Unknown.text] verbatim is what will let
 * compaction (§5) write those lines back out untouched. Lines that are not JSON at all are kept the
 * same way, for the same reason -- a half-written line from an interrupted Syncthing transfer is
 * not ours to delete.
 */
internal sealed class SharedRecord {
    data class Decision(
        val id: String,
        val createdAt: String,
        val createdBy: String,
        val window: TimeWindow,
        val signature: Signature,
        val resolution: Resolution,
        val scope: String,
    ) : SharedRecord()

    data class Tombstone(
        val id: String,
        val createdAt: String,
        val createdBy: String,
        val revokes: String,
    ) : SharedRecord()

    data class Setting(
        val key: String,
        val value: String,
        val updatedAt: String,
        val updatedBy: String,
    ) : SharedRecord()

    data class Unknown(val text: String) : SharedRecord()
}

/** The interval a decision applies to. Stored as the ISO-8601 strings we read. */
internal data class TimeWindow(val start: String, val end: String)

/**
 * One device's side of a contention ([`04_COMBINED_TIMELINE.md`] §3).
 *
 * [deviceUuid] is provenance, deliberately *not* part of [Signature.matchKey]: matching on
 * `device_role` is what lets a rule survive replacing a phone.
 */
internal data class Participant(
    val deviceRole: String,
    val deviceUuid: String,
    val app: String,
    val category: String,
)

/**
 * What was competing, canonically ordered so `{phone:YT, tablet:Kindle}` and the same pair written
 * the other way round are one key ([`04_COMBINED_TIMELINE.md`] §3).
 */
internal data class Signature(val participants: List<Participant>) {

    /** The rule key: role/app/category only, sorted -- no uuids, no ordering left to the writer. */
    val matchKey: String
        get() = participants
            .map { "${it.deviceRole}\u001F${it.app}\u001F${it.category}" }
            .sorted()
            .joinToString("\u001E")

    companion object {
        /** Sort on construction so the stored line is canonical too, not just the in-memory key. */
        fun of(participants: List<Participant>): Signature =
            Signature(
                participants.sortedWith(
                    compareBy({ it.deviceRole }, { it.app }, { it.category }, { it.deviceUuid })
                )
            )
    }
}

/** The chosen device+app when [Resolution.outcome] is `foreground`. */
internal data class ForegroundPick(val deviceRole: String, val app: String)

/**
 * What the owner decided (**R11** -- data, never an edit to the raw events).
 *
 * [outcome] stays a plain string rather than an enum: a newer build may record an outcome this one
 * has never heard of, and §8 says ignore what we do not understand, not drop the line carrying it.
 */
internal data class Resolution(
    val outcome: String,
    val foreground: ForegroundPick? = null,
    val label: String? = null,
    val deliberateBackground: List<String> = emptyList(),
)

// ---------------------------------------------------------------------------------------------
// Parsing
// ---------------------------------------------------------------------------------------------

/**
 * Parse one line. Blank lines return null -- they carry nothing, and preserving them would only
 * grow the file. Everything else returns a record, [SharedRecord.Unknown] included.
 */
internal fun parseSharedLine(line: String): SharedRecord? {
    if (line.isBlank()) return null
    val json = try {
        JSONObject(line)
    } catch (e: JSONException) {
        return SharedRecord.Unknown(line)
    }
    return when (json.optString("type")) {
        RECORD_TYPE_DECISION -> parseDecision(json) ?: SharedRecord.Unknown(line)
        RECORD_TYPE_TOMBSTONE -> parseTombstone(json) ?: SharedRecord.Unknown(line)
        RECORD_TYPE_SETTING -> parseSetting(json) ?: SharedRecord.Unknown(line)
        else -> SharedRecord.Unknown(line)
    }
}

/** Parse a whole file. CRLF is tolerated; blank lines are skipped. */
internal fun parseSharedJsonl(text: String?): List<SharedRecord> {
    if (text.isNullOrEmpty()) return emptyList()
    return text.split('\n').mapNotNull { parseSharedLine(it.removeSuffix("\r")) }
}

private fun parseDecision(json: JSONObject): SharedRecord.Decision? {
    val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return null
    val window = json.optJSONObject("window") ?: return null
    return SharedRecord.Decision(
        id = id,
        createdAt = json.optString("created_at"),
        createdBy = json.optString("created_by"),
        window = TimeWindow(window.optString("start"), window.optString("end")),
        signature = parseSignature(json.optJSONObject("signature")),
        resolution = parseResolution(json.optJSONObject("resolution")),
        scope = json.optString("scope").takeIf { it.isNotEmpty() } ?: SCOPE_ONCE,
    )
}

private fun parseSignature(json: JSONObject?): Signature {
    val array = json?.optJSONArray("participants") ?: return Signature.of(emptyList())
    val participants = (0 until array.length()).mapNotNull { i ->
        array.optJSONObject(i)?.let {
            Participant(
                deviceRole = it.optString("device_role"),
                deviceUuid = it.optString("device_uuid"),
                app = it.optString("app"),
                category = it.optString("category"),
            )
        }
    }
    return Signature.of(participants)
}

private fun parseResolution(json: JSONObject?): Resolution {
    if (json == null) return Resolution(outcome = "")
    val foreground = json.optJSONObject("foreground")
    val background = json.optJSONArray("deliberate_background")
    return Resolution(
        outcome = json.optString("outcome"),
        foreground = foreground?.let {
            ForegroundPick(it.optString("device_role"), it.optString("app"))
        },
        label = if (json.isNull("label")) {
            null
        } else {
            json.optString("label").takeIf { it.isNotEmpty() }
        },
        deliberateBackground = (0 until (background?.length() ?: 0)).mapNotNull { i ->
            background?.optString(i)?.takeIf { it.isNotEmpty() }
        },
    )
}

private fun parseTombstone(json: JSONObject): SharedRecord.Tombstone? {
    val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return null
    // A tombstone that revokes nothing is not a tombstone. Falling back to Unknown preserves the
    // line without letting an empty `revokes` match every decision that also lost its id.
    val revokes = json.optString("revokes").takeIf { it.isNotEmpty() } ?: return null
    return SharedRecord.Tombstone(
        id = id,
        createdAt = json.optString("created_at"),
        createdBy = json.optString("created_by"),
        revokes = revokes,
    )
}

private fun parseSetting(json: JSONObject): SharedRecord.Setting? {
    val key = json.optString("key").takeIf { it.isNotEmpty() } ?: return null
    return SharedRecord.Setting(
        key = key,
        value = json.optString("value"),
        updatedAt = json.optString("updated_at"),
        updatedBy = json.optString("updated_by"),
    )
}

// ---------------------------------------------------------------------------------------------
// Writing
// ---------------------------------------------------------------------------------------------

/** Serialise to exactly one line -- `toString()` without an indent never emits a newline. */
internal fun SharedRecord.Decision.toJsonLine(): String =
    JSONObject()
        .put("id", id)
        .put("type", RECORD_TYPE_DECISION)
        .put("created_at", createdAt)
        .put("created_by", createdBy)
        .put("window", JSONObject().put("start", window.start).put("end", window.end))
        .put(
            "signature",
            JSONObject().put(
                "participants",
                JSONArray().apply {
                    signature.participants.forEach {
                        put(
                            JSONObject()
                                .put("device_role", it.deviceRole)
                                .put("device_uuid", it.deviceUuid)
                                .put("app", it.app)
                                .put("category", it.category)
                        )
                    }
                }
            )
        )
        .put(
            "resolution",
            JSONObject()
                .put("outcome", resolution.outcome)
                .put(
                    "foreground",
                    resolution.foreground?.let {
                        JSONObject().put("device_role", it.deviceRole).put("app", it.app)
                    } ?: JSONObject.NULL
                )
                .put("label", resolution.label ?: JSONObject.NULL)
                .put("deliberate_background", JSONArray(resolution.deliberateBackground))
        )
        .put("scope", scope)
        .toString()

internal fun SharedRecord.Tombstone.toJsonLine(): String =
    JSONObject()
        .put("id", id)
        .put("type", RECORD_TYPE_TOMBSTONE)
        .put("created_at", createdAt)
        .put("created_by", createdBy)
        .put("revokes", revokes)
        .toString()

internal fun SharedRecord.Setting.toJsonLine(): String =
    JSONObject()
        .put("type", RECORD_TYPE_SETTING)
        .put("key", key)
        .put("value", value)
        .put("updated_at", updatedAt)
        .put("updated_by", updatedBy)
        .toString()

/** One line for any record, so a caller can append a mixed batch without a `when` of its own. */
internal fun SharedRecord.toJsonLine(): String = when (this) {
    is SharedRecord.Decision -> toJsonLine()
    is SharedRecord.Tombstone -> toJsonLine()
    is SharedRecord.Setting -> toJsonLine()
    is SharedRecord.Unknown -> text
}

// ---------------------------------------------------------------------------------------------
// Merging -- 05_DATA_MODEL.md §4.2 and §5
// ---------------------------------------------------------------------------------------------

/**
 * The effective decisions across every device ([`05_DATA_MODEL.md`] §4.2):
 *
 * 1. concatenate every device's lines (the caller does that -- [SharedFolder.readAllShared]);
 * 2. drop any decision revoked by a tombstone, **whichever file the tombstone came from** -- a
 *    tombstone routinely lives in a different device's file than the decision it revokes, which is
 *    exactly why revocation is resolved here and not on disk;
 * 3. group by `(window, signature)`;
 * 4. within a group keep the highest `created_at`; **ties break on lowest `created_by`.**
 *
 * Step 4's tiebreak is what makes three devices agree without coordination. "Most recently synced"
 * would not, and is not even available here by construction: nothing in this function can see when
 * a line arrived.
 *
 * Two additions the document does not spell out, both in service of **R18**:
 *
 * - **The same `id` seen twice is one decision.** Nothing stops a line being duplicated (a restored
 *   backup, a hand-copied file), and counting it twice could otherwise flip a tiebreak.
 * - **`id` is the last tiebreak**, after `created_by`. Two lines from *the same* device in the same
 *   millisecond for the same group would otherwise leave the winner to input order, which is
 *   precisely the property this function promises not to have.
 *
 * The result is sorted by window then id, so the *list* is order-independent too, not just its
 * contents.
 */
internal fun mergeDecisions(records: List<SharedRecord>): List<SharedRecord.Decision> {
    val revoked = records.filterIsInstance<SharedRecord.Tombstone>().map { it.revokes }.toSet()
    return records.asSequence()
        .filterIsInstance<SharedRecord.Decision>()
        .distinctBy { it.id }
        .filter { it.id !in revoked }
        .groupBy { "${it.window.start}\u001D${it.window.end}\u001D${it.signature.matchKey}" }
        .values
        .map { group -> group.minWith(decisionPrecedence) }
        .sortedWith(compareBy({ it.window.start }, { it.window.end }, { it.id }))
        .toList()
}

/** Lowest sorts first, so `minWith` returns the winner: newest, then lowest author, then lowest id. */
private val decisionPrecedence: Comparator<SharedRecord.Decision> =
    compareByDescending<SharedRecord.Decision> { instantOrMin(it.createdAt) }
        .thenByDescending { it.createdAt }
        .thenBy { it.createdBy }
        .thenBy { it.id }

/**
 * The effective value of every shared setting key ([`05_DATA_MODEL.md`] §5): highest `updated_at`
 * wins, ties break on lowest `updated_by` -- deliberately the same rule as decisions (**R18/R29**).
 *
 * Ordered by key, so two devices listing settings list them identically.
 */
internal fun effectiveSettings(records: List<SharedRecord>): Map<String, SharedRecord.Setting> {
    val winners = LinkedHashMap<String, SharedRecord.Setting>()
    records.filterIsInstance<SharedRecord.Setting>()
        .groupBy { it.key }
        .toSortedMap()
        .forEach { (key, group) -> winners[key] = group.minWith(settingPrecedence) }
    return winners
}

/** Values without provenance, for callers that only want the answer (2.3 routes `category.*` here). */
internal fun effectiveSettingValues(records: List<SharedRecord>): Map<String, String> =
    effectiveSettings(records).mapValues { it.value.value }

private val settingPrecedence: Comparator<SharedRecord.Setting> =
    compareByDescending<SharedRecord.Setting> { instantOrMin(it.updatedAt) }
        .thenByDescending { it.updatedAt }
        .thenBy { it.updatedBy }
        .thenBy { it.value }

/**
 * A timestamp's sort position. Unparseable or missing timestamps sort *below* every real one --
 * they lose -- but still compare deterministically against each other, because the comparators
 * above fall back to the raw string. Guessing at a broken timestamp would reintroduce exactly the
 * arrival-order dependence **R18** forbids.
 */
private fun instantOrMin(raw: String): Instant =
    try {
        Instant.parse(raw)
    } catch (e: DateTimeParseException) {
        Instant.MIN
    }

// ---------------------------------------------------------------------------------------------
// Identifiers
// ---------------------------------------------------------------------------------------------

private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

/**
 * ULIDs for decision and tombstone ids ([`05_DATA_MODEL.md`] §4).
 *
 * A ULID is 48 bits of millisecond timestamp then 80 bits of randomness, Crockford base32, 26
 * characters. Two properties earn it over a plain UUID: ids sort chronologically as plain strings,
 * so an append-only log is readable and diffable in order; and 80 random bits make a collision
 * between devices that never talk to each other a non-event -- which matters because no device here
 * can ask another whether an id is already taken (**R20**, **R22**).
 */
internal object Ulid {
    fun generate(now: Instant = Instant.now(), random: Random = Random.Default): String {
        val chars = CharArray(26)
        var time = now.toEpochMilli()
        // 10 characters hold 50 bits, comfortably above the 48 a millisecond timestamp needs;
        // filling backwards puts the most significant character first, which is what makes the
        // string sort chronologically.
        for (i in 9 downTo 0) {
            chars[i] = CROCKFORD[(time and 0x1fL).toInt()]
            time = time shr 5
        }
        for (i in 10 until 26) {
            chars[i] = CROCKFORD[random.nextInt(32)]
        }
        return String(chars)
    }

    /** `d_…` per §4's example -- the prefix makes a stray id in a log obvious at a glance. */
    fun decisionId(now: Instant = Instant.now(), random: Random = Random.Default): String =
        "d_" + generate(now, random)

    /** `t_…` per §4.1. */
    fun tombstoneId(now: Instant = Instant.now(), random: Random = Random.Default): String =
        "t_" + generate(now, random)
}

// ---------------------------------------------------------------------------------------------
// Decision sync -- 05_DATA_MODEL.md §4, roadmap 4.2
// ---------------------------------------------------------------------------------------------

/**
 * What one decision-sync cycle has to move, in both directions.
 *
 * Both halves are **raw lines**, never records: the whole contract of `decisions.jsonl` is that the
 * line a device wrote is the line every other device reads. See [SharedFolder.appendSharedLines].
 */
internal data class DecisionPlan(
    /** Our own decisions that are not yet in our own file in the shared folder. */
    val linesToPublish: List<String>,
    /** Lines in the shared folder that this device's server does not hold yet. */
    val linesToImport: List<String>,
) {
    val isEmpty: Boolean get() = linesToPublish.isEmpty() && linesToImport.isEmpty()
}

/**
 * Decide which decision lines to publish and which to import.
 *
 * Unlike [planSettingsSync] this needs **no memory of what it did last time**, and that is the
 * point. A setting has one current value, so "did the owner change it here, or has a peer's change
 * not arrived yet?" can only be answered by remembering what we last agreed to. Decisions are
 * append-only records with globally unique ids, so the question is only ever *is this id present*,
 * which both sides can answer from what they are holding right now. That makes this function
 * idempotent: running it twice in a row publishes and imports nothing the second time.
 *
 * @param localLines every record the server holds, as raw lines (ours and peers' alike).
 * @param sharedLines every line in every device's `decisions.jsonl`.
 * @param deviceUuid this device -- only records it authored are ours to publish (**R20**: one
 *   writer per file, so a peer's decision that reached us through the server is never written into
 *   our file, only into the one its author owns).
 *
 * A line neither side can parse is left where it is: not published, not imported, not deleted
 * (**§8**). It is either a newer build's record or a half-written transfer, and both outlast us.
 */
internal fun planDecisionSync(
    localLines: List<String>,
    sharedLines: List<String>,
    deviceUuid: String,
): DecisionPlan {
    val sharedIds = sharedLines.mapNotNull { idOfRecord(it) }.toSet()
    val localIds = localLines.mapNotNull { idOfRecord(it) }.toSet()

    val toPublish = mutableListOf<String>()
    val publishing = mutableSetOf<String>()
    for (line in localLines) {
        val record = parseSharedLine(line) ?: continue
        val id = idOf(record) ?: continue
        if (authorOf(record) != deviceUuid) continue // not ours to write (R20)
        if (id in sharedIds || !publishing.add(id)) continue
        toPublish += line
    }

    val toImport = mutableListOf<String>()
    val importing = mutableSetOf<String>()
    for (line in sharedLines) {
        val id = idOfRecord(line) ?: continue
        if (id in localIds || !importing.add(id)) continue
        toImport += line
    }
    return DecisionPlan(toPublish, toImport)
}

/** The id of a decision or tombstone line, or null for anything else (a setting, a broken line). */
private fun idOfRecord(line: String): String? = parseSharedLine(line)?.let { idOf(it) }

private fun idOf(record: SharedRecord): String? = when (record) {
    is SharedRecord.Decision -> record.id
    is SharedRecord.Tombstone -> record.id
    else -> null
}

private fun authorOf(record: SharedRecord): String? = when (record) {
    is SharedRecord.Decision -> record.createdBy
    is SharedRecord.Tombstone -> record.createdBy
    else -> null
}
