package net.activitywatch.android

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

private const val TAG = "SharedFolder"

/**
 * The schema version this build reads and writes. Bump it only for a change an older build would
 * *misread* -- [`05_DATA_MODEL.md`] §8 makes a bump a refusal on every device that has not been
 * updated, which is the point, but it also stops those devices merging at all.
 */
internal const val SHARED_SCHEMA_VERSION = 1

internal const val SHARED_VERSION_FILE = "VERSION"
internal const val SHARED_DEVICES_DIR = "devices"
internal const val SHARED_META_FILE = "meta.json"

/** A tablet is a device whose narrow side is at least this many dp -- the platform's own cutoff. */
private const val TABLET_SMALLEST_WIDTH_DP = 600

/**
 * What the shared folder's `VERSION` file says about whether we may merge.
 *
 * [ABSENT] and [TOO_NEW] are the two that carry a decision: the first means we are the first
 * device here and should write our own version; the second means stop. [MALFORMED] blocks too, on
 * purpose -- a `VERSION` we cannot parse is likelier to have been written by something newer than
 * by accident, and §8's rule is that a silent misread is unrecoverable where a refusal is not.
 */
internal enum class SchemaVerdict {
    COMPATIBLE,
    ABSENT,
    MALFORMED,
    TOO_NEW;

    val blocksMerge: Boolean
        get() = this == MALFORMED || this == TOO_NEW
}

/** Judge `VERSION`'s contents. [raw] is null when the file does not exist. */
internal fun schemaVerdict(raw: String?): SchemaVerdict {
    if (raw == null) return SchemaVerdict.ABSENT
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return SchemaVerdict.MALFORMED
    val found = trimmed.toIntOrNull() ?: return SchemaVerdict.MALFORMED
    // A *lower* version is compatible: this build knows every earlier layout, and the older device
    // that wrote it still reads what it wrote. We only raise the file when we add to the layout,
    // which 2.1 does not.
    return if (found > SHARED_SCHEMA_VERSION) SchemaVerdict.TOO_NEW else SchemaVerdict.COMPATIBLE
}

/**
 * One device's `meta.json` ([`05_DATA_MODEL.md`] §3): who it is and what it can do.
 *
 * Rewritten wholesale by its owner and nobody else (**R20**), which is what makes rewriting safe.
 * Device-local settings -- the sync folder URI above all -- are deliberately absent (**R28**).
 */
internal data class DeviceMeta(
    val deviceUuid: String,
    val displayName: String,
    val role: String,
    val platform: String,
    val appVersion: String,
    val lastSeen: String,
    val schemaVersion: Int = SHARED_SCHEMA_VERSION,
)

internal fun DeviceMeta.toJson(): String =
    JSONObject()
        .put("device_uuid", deviceUuid)
        .put("display_name", displayName)
        .put("role", role)
        .put("platform", platform)
        .put("app_version", appVersion)
        .put("last_seen", lastSeen)
        .put("schema_version", schemaVersion)
        .toString(2)

/**
 * Parse a `meta.json`, or null if it is not one.
 *
 * Unknown fields are ignored rather than rejected: a newer device may write more than we know
 * about, and §8's rule is that we must not destroy what we do not understand. Ignoring is enough
 * here because we never rewrite a peer's file. The other half of §8's rule -- a compaction that
 * must write back the lines it did not understand -- has nothing to do yet: 2.2's JSONL logs are
 * append-only and nothing compacts them ([SharedRecord.Unknown] keeps the raw line for when it
 * does).
 *
 * Deliberately free of `android.util.Log` so it is reachable from plain JVM unit tests; the
 * caller says what a null means in its own context.
 */
internal fun parseDeviceMeta(text: String?): DeviceMeta? {
    if (text.isNullOrBlank()) return null
    return try {
        val json = JSONObject(text)
        val uuid = json.optString("device_uuid").takeIf { it.isNotEmpty() } ?: return null
        DeviceMeta(
            deviceUuid = uuid,
            displayName = json.optString("display_name"),
            role = json.optString("role"),
            platform = json.optString("platform"),
            appVersion = json.optString("app_version"),
            lastSeen = json.optString("last_seen"),
            schemaVersion = json.optInt("schema_version", SHARED_SCHEMA_VERSION),
        )
    } catch (e: JSONException) {
        null
    }
}

/**
 * Whether a `meta.json` already in the shared folder under *our* uuid looks like another device
 * wrote it -- the restore-guard signal from [`05_DATA_MODEL.md`] §7, where an app backup restored
 * onto a second device clones aw-server's `device_id` file and two devices start writing one
 * directory (Blocker 3, `03_SYNC.md` §2.3).
 *
 * Only a differing [DeviceMeta.platform] counts. `app_version` was the other half of §7's
 * suggested test and is not usable on its own: it changes on every ordinary app update, so it
 * would fire on a single device that merely updated between two syncs.
 *
 * WARNING: this detects, it does not fix. The uuid is minted and persisted by the embedded server
 * ([SyncInterface.resolveDeviceId] reads it), so nothing here can mint a replacement without
 * creating a second identity for this device -- the thing that function exists to refuse. Until
 * the server side grows a re-mint call, a hit is a loud log line and nothing more.
 */
internal fun looksLikeForeignLineage(ours: DeviceMeta, existing: DeviceMeta): Boolean =
    existing.deviceUuid == ours.deviceUuid &&
        existing.platform.isNotEmpty() &&
        existing.platform != ours.platform

/** `phone` or `tablet` per §3's `role` -- the key a decision signature survives a device swap on. */
internal fun deviceRole(context: Context): String =
    if (context.resources.configuration.smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP) {
        "tablet"
    } else {
        "phone"
    }

/**
 * Whether a top-level entry in the shared folder is shared state rather than a device hostname.
 *
 * The SAF import walks the root expecting `<hostname>/<device_id>/`. `devices/` sits at that same
 * level, so without this every peer's `devices/<uuid>/` would be copied into app-private storage
 * as though it were a peer's database directory.
 */
internal fun isSharedStateDir(name: String): Boolean =
    name == SHARED_DEVICES_DIR || name == SHARED_VERSION_FILE

/**
 * The shared folder's device-independent state: the root `VERSION` file and `devices/<uuid>/`.
 *
 * **This is deliberately not where `events.db` lives.** [`05_DATA_MODEL.md`] §2 draws the layout
 * with the database inside `devices/<uuid>/`, but that path is chosen by aw-sync's
 * `setup_local_remote` in Rust (`<hostname>/<device_id>/test.db`), verified on hardware right
 * through Phase 1 and walked by `find_remotes` on the way back in. Moving it is a Rust change that
 * would invalidate every on-device check Phase 1 made; adding `devices/` beside it costs nothing
 * and is what 2.2's append-only files actually need. §2 of that document records the deviation.
 */
internal class SharedFolder(private val context: Context, private val root: DocumentFile) {

    /**
     * Read `VERSION`, creating it when we are the first device to arrive.
     *
     * Failing to *write* it when it is absent reports [SchemaVerdict.ABSENT] rather than a block:
     * an unwritable root is already reported by the transfer that follows, and refusing here as
     * well would turn one problem into two messages.
     */
    fun ensureVersion(): SchemaVerdict {
        val existing = root.findFile(SHARED_VERSION_FILE)
        if (existing != null && !existing.isFile) {
            Log.w(TAG, "$SHARED_VERSION_FILE exists in the sync folder but is not a file")
            return SchemaVerdict.MALFORMED
        }
        if (existing != null) {
            val verdict = schemaVerdict(readText(existing))
            if (verdict.blocksMerge) {
                Log.w(
                    TAG,
                    "Shared folder $SHARED_VERSION_FILE is $verdict; this build speaks " +
                        "v$SHARED_SCHEMA_VERSION",
                )
            }
            return verdict
        }
        // "application/octet-stream" is deliberate, not lazy: SAF's local-storage provider maps
        // "text/plain" to a preferred extension and silently renames a create of "VERSION" to
        // "VERSION.txt" -- confirmed on device 2026-09-09, where findFile("VERSION") then never
        // matched what was actually written and every sync re-created another copy
        // ("VERSION.txt", "VERSION (1).txt", "VERSION (2).txt", ...). octet-stream has no
        // preferred extension, so the name we ask for is the name we get -- the same reason
        // mirrorDirectory below uses it for every file it writes.
        val created = root.createFile("application/octet-stream", SHARED_VERSION_FILE)
        if (created == null || !writeText(created, "$SHARED_SCHEMA_VERSION\n")) {
            Log.w(TAG, "Could not write $SHARED_VERSION_FILE")
            return SchemaVerdict.ABSENT
        }
        Log.i(TAG, "Wrote $SHARED_VERSION_FILE=$SHARED_SCHEMA_VERSION to a fresh shared folder")
        return SchemaVerdict.COMPATIBLE
    }

    /**
     * Publish `devices/<uuid>/meta.json`, replaced wholesale (**R20** -- we are its only writer).
     *
     * The existing file is read first, so an identity clone left by a restored backup is noticed
     * before we overwrite the evidence of it; see [looksLikeForeignLineage].
     *
     * @return true when the file is on disk holding our contents.
     */
    fun writeMeta(meta: DeviceMeta): Boolean {
        val devicesDir = findOrCreateDir(root, SHARED_DEVICES_DIR) ?: return false
        val deviceDir = findOrCreateDir(devicesDir, meta.deviceUuid) ?: return false

        val existingFile = deviceDir.findFile(SHARED_META_FILE)
        if (existingFile != null && existingFile.isDirectory) {
            Log.w(TAG, "$SHARED_META_FILE exists as a directory; cannot write meta")
            return false
        }
        val existingText = existingFile?.let { readText(it) }
        val existingMeta = parseDeviceMeta(existingText)
        if (existingMeta == null && !existingText.isNullOrBlank()) {
            // Something is there and it is not a meta.json we recognise. We are its only writer,
            // so replacing it is correct -- but it is worth saying that we did.
            Log.w(TAG, "Replacing an unrecognised $SHARED_META_FILE for ${meta.deviceUuid}")
        }
        if (existingMeta != null && looksLikeForeignLineage(meta, existingMeta)) {
            // Two devices sharing one uuid write one directory -- Blocker 3 all over again. We
            // cannot mint a new identity from here, so say it as loudly as a log line can.
            Log.e(
                TAG,
                "Shared folder already lists ${meta.deviceUuid} as platform=${existingMeta.platform} " +
                    "but we are ${meta.platform}. A restored backup may have cloned this device's " +
                    "identity -- see 05_DATA_MODEL.md section 7.",
            )
        }

        val file = existingFile ?: deviceDir.createFile("application/json", SHARED_META_FILE)
        if (file == null) {
            Log.w(TAG, "Could not create $SHARED_META_FILE for ${meta.deviceUuid}")
            return false
        }
        return writeText(file, meta.toJson())
    }

    /**
     * Every device directory under `devices/`, by uuid -- ours included.
     *
     * A device is "present" here because it wrote something, not because it is online: a phone that
     * has been off for a month still has its uuid listed, and its decisions still count (**R23** --
     * a peer may reappear arbitrarily late and must not have been merged away in the meantime).
     */
    fun listDeviceUuids(): List<String> {
        val devicesDir = root.findFile(SHARED_DEVICES_DIR) ?: return emptyList()
        if (!devicesDir.isDirectory) return emptyList()
        return devicesDir.listFiles()
            .filter { it.isDirectory }
            .mapNotNull { it.name }
            .sorted()
    }

    /** Parse one device's copy of [fileName]; an absent or unreadable file is simply no records. */
    fun readShared(deviceUuid: String, fileName: String): List<SharedRecord> {
        val devicesDir = root.findFile(SHARED_DEVICES_DIR) ?: return emptyList()
        val deviceDir = devicesDir.findFile(deviceUuid) ?: return emptyList()
        val file = deviceDir.findFile(fileName)?.takeIf { it.isFile } ?: return emptyList()
        return parseSharedJsonl(readText(file))
    }

    /**
     * Every device's copy of [fileName], concatenated -- the input [mergeDecisions] and
     * [effectiveSettings] expect.
     *
     * Concatenation order is uuid order, which is arbitrary but *stable*; the merge does not depend
     * on it either way (**R18**), and a stable order keeps a logged dump comparable between runs.
     * A device whose file cannot be read contributes nothing rather than failing the read: one
     * unreadable peer must not cost us the other two.
     */
    fun readAllShared(fileName: String): List<SharedRecord> =
        listDeviceUuids().flatMap { readShared(it, fileName) }

    /**
     * Append [records] to our own `devices/<uuid>/<fileName>` (**R20** -- only the owner writes it).
     *
     * Appending, never rewriting, is the whole point: a whole-file replacement is what makes
     * Syncthing produce `.sync-conflict-*` copies, and a log that is only ever extended cannot lose
     * a line to one.
     *
     * SAF's `"wa"` mode is the real append and is what we ask for first. Not every provider
     * implements it, so the fallback reads the file and rewrites it with the new lines on the end.
     * That fallback is only safe because we are this file's only writer -- never use it on a peer's.
     *
     * @return true when every line is on disk. An empty [records] writes nothing and succeeds.
     */
    fun appendShared(deviceUuid: String, fileName: String, records: List<SharedRecord>): Boolean {
        if (records.isEmpty()) return true
        val devicesDir = findOrCreateDir(root, SHARED_DEVICES_DIR) ?: return false
        val deviceDir = findOrCreateDir(devicesDir, deviceUuid) ?: return false

        val existing = deviceDir.findFile(fileName)
        if (existing != null && existing.isDirectory) {
            Log.w(TAG, "$fileName exists as a directory; cannot append")
            return false
        }
        // "application/octet-stream" for the same reason VERSION uses it: SAF's local-storage
        // provider rewrites a filename to match its idea of the MIME type's extension, and a
        // decisions.jsonl silently written as decisions.jsonl.txt would never be found again.
        val file = existing ?: deviceDir.createFile("application/octet-stream", fileName)
        if (file == null) {
            Log.w(TAG, "Could not create $fileName for $deviceUuid")
            return false
        }
        // Every line this app writes is newline-terminated, so a plain append lands on a fresh
        // line. A file whose last line was truncated mid-transfer is the exception, and the parser
        // keeps that damaged line as SharedRecord.Unknown rather than letting it eat ours.
        val text = records.joinToString("") { it.toJsonLine() + "\n" }
        return appendText(file, text)
    }

    private fun appendText(file: DocumentFile, text: String): Boolean {
        try {
            context.contentResolver.openOutputStream(file.uri, "wa")?.use {
                it.write(text.toByteArray())
                return true
            }
            Log.w(TAG, "No output stream for ${file.name} in append mode; rewriting instead")
        } catch (e: IOException) {
            Log.w(TAG, "Append not available for ${file.name} (${e.message}); rewriting instead")
        } catch (e: IllegalArgumentException) {
            // Providers that do not know "wa" throw this rather than returning null.
            Log.w(TAG, "Append mode rejected for ${file.name} (${e.message}); rewriting instead")
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission denied appending to ${file.name}: ${e.message}")
            return false
        }
        val existingText = readText(file) ?: ""
        val separator = if (existingText.isEmpty() || existingText.endsWith("\n")) "" else "\n"
        return writeText(file, existingText + separator + text)
    }

    private fun findOrCreateDir(parent: DocumentFile, name: String): DocumentFile? {
        val existing = parent.findFile(name)
        if (existing != null && !existing.isDirectory) {
            Log.w(TAG, "$name exists in the sync folder as a file, not a directory")
            return null
        }
        val dir = existing ?: parent.createDirectory(name)
        if (dir == null) Log.w(TAG, "Could not create $name in the sync folder")
        return dir
    }

    private fun readText(file: DocumentFile): String? =
        try {
            context.contentResolver.openInputStream(file.uri)?.use {
                it.readBytes().decodeToString()
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not read ${file.name}: ${e.message}")
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission denied reading ${file.name}: ${e.message}")
            null
        }

    private fun writeText(file: DocumentFile, text: String): Boolean =
        try {
            // "wt" truncates: these files are replaced wholesale, never appended to.
            context.contentResolver.openOutputStream(file.uri, "wt")?.use {
                it.write(text.toByteArray())
                true
            } ?: false
        } catch (e: IOException) {
            Log.w(TAG, "Could not write ${file.name}: ${e.message}")
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission denied writing ${file.name}: ${e.message}")
            false
        }

    companion object {
        /** Open the shared folder at [uriStr], or null if the grant is gone or it is not a tree. */
        fun open(context: Context, uriStr: String): SharedFolder? {
            val dir = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
            if (dir == null || !dir.isDirectory) return null
            return SharedFolder(context, dir)
        }
    }
}
