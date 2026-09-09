package net.activitywatch.android

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedFolderTest {

    private fun meta(uuid: String = "9f2c1e84-3b7a-4c55-8d21-6ae0f5b91c73", platform: String = "android") =
        DeviceMeta(
            deviceUuid = uuid,
            displayName = "judes_phone",
            role = "phone",
            platform = platform,
            appVersion = "0.14.0b2",
            lastSeen = "2026-09-09T10:11:12Z",
        )

    @Test
    fun schemaVerdict_absentWhenNoFile() {
        assertEquals(SchemaVerdict.ABSENT, schemaVerdict(null))
        assertFalse(SchemaVerdict.ABSENT.blocksMerge)
    }

    @Test
    fun schemaVerdict_acceptsOurVersionWithTrailingNewline() {
        assertEquals(SchemaVerdict.COMPATIBLE, schemaVerdict("$SHARED_SCHEMA_VERSION\n"))
    }

    @Test
    fun schemaVerdict_acceptsOlderVersion() {
        // Only a *newer* folder is unreadable to us; an older one we still understand.
        assertEquals(SchemaVerdict.COMPATIBLE, schemaVerdict("${SHARED_SCHEMA_VERSION - 1}"))
    }

    @Test
    fun schemaVerdict_refusesNewerVersion() {
        val verdict = schemaVerdict("${SHARED_SCHEMA_VERSION + 1}\n")
        assertEquals(SchemaVerdict.TOO_NEW, verdict)
        assertTrue(verdict.blocksMerge)
    }

    @Test
    fun schemaVerdict_refusesUnparseableVersion() {
        // Junk is likelier to be a newer writer than an accident, and guessing is the one
        // outcome 05_DATA_MODEL.md section 8 rules out.
        for (raw in listOf("", "   ", "one", "1.0", "1 2")) {
            assertEquals("for '$raw'", SchemaVerdict.MALFORMED, schemaVerdict(raw))
        }
        assertTrue(SchemaVerdict.MALFORMED.blocksMerge)
    }

    @Test
    fun meta_roundTripsThroughJson() {
        val original = meta()
        assertEquals(original, parseDeviceMeta(original.toJson()))
    }

    @Test
    fun meta_writesTheDocumentedFieldNames() {
        val json = JSONObject(meta().toJson())
        assertEquals("9f2c1e84-3b7a-4c55-8d21-6ae0f5b91c73", json.getString("device_uuid"))
        assertEquals("judes_phone", json.getString("display_name"))
        assertEquals("phone", json.getString("role"))
        assertEquals("android", json.getString("platform"))
        assertEquals("0.14.0b2", json.getString("app_version"))
        assertEquals("2026-09-09T10:11:12Z", json.getString("last_seen"))
        assertEquals(SHARED_SCHEMA_VERSION, json.getInt("schema_version"))
    }

    @Test
    fun meta_keepsUnknownFieldsFromBreakingTheParse() {
        // A newer device may write more than we know about; section 8 says ignore, never reject.
        val text = """
            { "device_uuid": "abc", "platform": "android", "capabilities": ["timeline"] }
        """.trimIndent()
        val parsed = parseDeviceMeta(text)
        assertEquals("abc", parsed?.deviceUuid)
        assertEquals("android", parsed?.platform)
    }

    @Test
    fun meta_rejectsWhatIsNotMeta() {
        assertNull(parseDeviceMeta(null))
        assertNull(parseDeviceMeta(""))
        assertNull(parseDeviceMeta("not json"))
        // No device_uuid: nothing here can be attributed, so it is not a meta.json.
        assertNull(parseDeviceMeta("""{"display_name": "someone"}"""))
    }

    @Test
    fun foreignLineage_firesOnlyForAnotherPlatformUnderOurUuid() {
        val ours = meta()
        assertTrue(looksLikeForeignLineage(ours, meta(platform = "linux")))
        // Our own earlier export, and an ordinary app update, must not look like a clone.
        assertFalse(looksLikeForeignLineage(ours, ours))
        assertFalse(looksLikeForeignLineage(ours, ours.copy(appVersion = "0.15.0")))
        // Another device's uuid is simply another device.
        assertFalse(looksLikeForeignLineage(ours, meta(uuid = "other", platform = "linux")))
        // An older meta.json without a platform tells us nothing; do not cry wolf.
        assertFalse(looksLikeForeignLineage(ours, meta(platform = "")))
    }

    @Test
    fun sharedStateDirs_areNotHostnames() {
        assertTrue(isSharedStateDir(SHARED_DEVICES_DIR))
        assertTrue(isSharedStateDir(SHARED_VERSION_FILE))
        assertFalse(isSharedStateDir("judes_phone"))
        assertFalse(isSharedStateDir("tab_s10fe"))
    }
}
