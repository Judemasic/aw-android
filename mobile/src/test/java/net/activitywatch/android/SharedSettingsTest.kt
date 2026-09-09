package net.activitywatch.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The routing decision from `SharedSettings.kt`: what this device publishes, what it accepts, and
 * what it remembers agreeing to.
 *
 * The scenario tests run a plan, feed its result back in as the next cycle's state, and check that
 * the second cycle does nothing. A settings sync that republishes on every cycle would still
 * "work" from the outside while growing the log forever, so quietness is asserted, not assumed.
 */
class SharedSettingsTest {

    private val phone = "1111-aaaa"
    private val tablet = "2222-bbbb"
    private val fun_ = """[{"id":1,"name":["fun"]}]"""
    private val video = """[{"id":1,"name":["video"]}]"""

    private fun merged(vararg settings: SharedRecord.Setting) =
        effectiveSettings(settings.toList())

    // -- what is shared ------------------------------------------------------------------------

    @Test
    fun sharedKeys_areMeaningNotDevice() {
        assertTrue(isSharedSettingKey("classes"))
        assertTrue(isSharedSettingKey("category_sets"))
        assertTrue(isSharedSettingKey("startOfDay"))
        // The namespaces 05_DATA_MODEL.md section 5 reserved, for Phase 4's rules.
        assertTrue(isSharedSettingKey("category.com.google.android.youtube"))
        assertTrue(isSharedSettingKey("label.work"))
        assertTrue(isSharedSettingKey("rule.evening"))
    }

    @Test
    fun localKeys_neverLeaveTheDevice() {
        // R28. Every key aw-webui stores that is about this device rather than about meaning.
        for (key in DELIBERATELY_LOCAL_SETTING_KEYS) {
            assertFalse(key, isSharedSettingKey(key))
        }
        // And the allowlist is an allowlist: a key nobody has heard of stays put.
        assertFalse(isSharedSettingKey("someFutureWebuiSetting"))
        assertFalse(isSharedSettingKey(""))
    }

    @Test
    fun theTwoListsDoNotOverlap() {
        assertEquals(emptySet<String>(), SHARED_SETTING_KEYS intersect DELIBERATELY_LOCAL_SETTING_KEYS)
    }

    // -- publishing ----------------------------------------------------------------------------

    @Test
    fun firstRun_publishesWhatTheOwnerHasAlreadySaved() {
        val plan = planSettingsSync(
            local = mapOf("classes" to video, "theme" to "\"dark\""),
            merged = emptyMap(),
            applied = emptyMap(),
            now = "2026-09-09T12:00:00Z",
            deviceUuid = phone,
        )
        assertEquals(listOf("classes"), plan.linesToAppend.map { it.key })
        assertEquals(video, plan.linesToAppend.single().value)
        assertEquals(phone, plan.linesToAppend.single().updatedBy)
        // theme is this device's business and is not published, applied, or remembered.
        assertEquals(emptyMap<String, String>(), plan.valuesToApply)
        assertEquals(mapOf("classes" to video), plan.applied)
    }

    @Test
    fun anUnchangedDeviceSaysNothing() {
        val plan = planSettingsSync(
            local = mapOf("classes" to video),
            merged = merged(SharedRecord.Setting("classes", video, "2026-09-08T10:00:00Z", phone)),
            applied = mapOf("classes" to video),
            now = "2026-09-09T12:00:00Z",
            deviceUuid = phone,
        )
        assertTrue(plan.isEmpty)
    }

    @Test
    fun aLocalEditIsPublishedAndThenTheDeviceGoesQuiet() {
        // The owner renames the category on this device; the shared folder still has the old value.
        val first = planSettingsSync(
            local = mapOf("classes" to fun_),
            merged = merged(SharedRecord.Setting("classes", video, "2026-09-08T10:00:00Z", phone)),
            applied = mapOf("classes" to video),
            now = "2026-09-09T12:00:00Z",
            deviceUuid = phone,
        )
        assertEquals(listOf("classes"), first.linesToAppend.map { it.key })
        assertEquals(fun_, first.linesToAppend.single().value)
        assertEquals(emptyMap<String, String>(), first.valuesToApply)

        // Next cycle, with our own line now in the folder: nothing more to say.
        val second = planSettingsSync(
            local = mapOf("classes" to fun_),
            merged = merged(first.linesToAppend.single()),
            applied = first.applied,
            now = "2026-09-09T12:30:00Z",
            deviceUuid = phone,
        )
        assertTrue("a second cycle must not republish", second.isEmpty)
    }

    // -- accepting -----------------------------------------------------------------------------

    @Test
    fun aPeersEditIsAppliedAndNotEchoedBack() {
        // This is the check 2.3 exists for: renamed on the phone, showing up on the tablet.
        val first = planSettingsSync(
            local = mapOf("classes" to video),
            merged = merged(SharedRecord.Setting("classes", fun_, "2026-09-09T12:00:00Z", phone)),
            applied = mapOf("classes" to video),
            now = "2026-09-09T12:05:00Z",
            deviceUuid = tablet,
        )
        assertEquals(mapOf("classes" to fun_), first.valuesToApply)
        assertEquals(emptyList<SharedRecord.Setting>(), first.linesToAppend)

        // The value is now local here too. Without `applied` being updated, this cycle would look
        // exactly like a local edit and the tablet would republish the phone's own change.
        val second = planSettingsSync(
            local = mapOf("classes" to fun_),
            merged = merged(SharedRecord.Setting("classes", fun_, "2026-09-09T12:00:00Z", phone)),
            applied = first.applied,
            now = "2026-09-09T12:35:00Z",
            deviceUuid = tablet,
        )
        assertTrue("an applied value must not be echoed back", second.isEmpty)
    }

    @Test
    fun aFreshDeviceAdoptsWhatItHasNeverHeldItself() {
        // Nothing local at all: a new tablet joining an established folder.
        val plan = planSettingsSync(
            local = emptyMap(),
            merged = merged(
                SharedRecord.Setting("classes", fun_, "2026-09-09T12:00:00Z", phone),
                SharedRecord.Setting("startOfDay", "\"05:00\"", "2026-09-09T12:00:00Z", phone),
            ),
            applied = emptyMap(),
            now = "2026-09-09T12:05:00Z",
            deviceUuid = tablet,
        )
        assertEquals(mapOf("classes" to fun_, "startOfDay" to "\"05:00\""), plan.valuesToApply)
        assertEquals(emptyList<SharedRecord.Setting>(), plan.linesToAppend)
    }

    @Test
    fun aKeyTheOwnerNeverSavedIsNotPublishedAsADefault() {
        // The datastore holds nothing for an untouched key, so "absent locally" means "no opinion".
        // Publishing an absence would mean overwriting a peer's real choice with our defaults.
        val plan = planSettingsSync(
            local = emptyMap(),
            merged = emptyMap(),
            applied = emptyMap(),
            now = "2026-09-09T12:00:00Z",
            deviceUuid = phone,
        )
        assertTrue(plan.isEmpty)
        assertEquals(emptyMap<String, String>(), plan.applied)
    }

    // -- both at once --------------------------------------------------------------------------

    @Test
    fun twoDevicesEditingAtOnceConvergeOnTheSameValue() {
        val phoneEdit = SharedRecord.Setting("classes", fun_, "2026-09-09T12:00:00Z", phone)
        val tabletEdit = SharedRecord.Setting("classes", video, "2026-09-09T12:00:00Z", tablet)
        // Same second, so R29's tiebreak decides: lowest updated_by, which is the phone.
        val winner = merged(phoneEdit, tabletEdit)
        assertEquals(fun_, winner["classes"]?.value)

        // The loser's next cycle: it already published its own value, so local == applied, and it
        // simply accepts the winner rather than fighting for its own.
        val tabletNext = planSettingsSync(
            local = mapOf("classes" to video),
            merged = winner,
            applied = mapOf("classes" to video),
            now = "2026-09-09T12:30:00Z",
            deviceUuid = tablet,
        )
        assertEquals(mapOf("classes" to fun_), tabletNext.valuesToApply)
        assertEquals(emptyList<SharedRecord.Setting>(), tabletNext.linesToAppend)

        // And the winner's next cycle is quiet, so the two do not trade the value back and forth.
        val phoneNext = planSettingsSync(
            local = mapOf("classes" to fun_),
            merged = winner,
            applied = mapOf("classes" to fun_),
            now = "2026-09-09T12:30:00Z",
            deviceUuid = phone,
        )
        assertTrue(phoneNext.isEmpty)
    }

    @Test
    fun aLocalEditBeatsAPeersOlderOneEvenWhenThePeerLineIsNewerInTheFolder() {
        // The owner changed it here since we last agreed. That is news; the folder's value is not.
        val plan = planSettingsSync(
            local = mapOf("classes" to fun_),
            merged = merged(SharedRecord.Setting("classes", video, "2026-09-09T13:00:00Z", tablet)),
            applied = mapOf("classes" to """[{"id":1,"name":["old"]}]"""),
            now = "2026-09-09T13:05:00Z",
            deviceUuid = phone,
        )
        assertEquals(listOf("classes"), plan.linesToAppend.map { it.key })
        assertEquals("2026-09-09T13:05:00Z", plan.linesToAppend.single().updatedAt)
        // Published, not overwritten -- and the next merge decides between the two on timestamp.
        assertEquals(emptyMap<String, String>(), plan.valuesToApply)
        assertEquals(fun_, plan.applied["classes"])
    }

    @Test
    fun localOnlySettingsAreNeverPublishedAppliedOrRemembered() {
        val plan = planSettingsSync(
            local = mapOf("theme" to "\"dark\"", "views" to "[]", "landingpage" to "\"/home\""),
            merged = merged(SharedRecord.Setting("theme", "\"light\"", "2026-09-09T12:00:00Z", tablet)),
            applied = emptyMap(),
            now = "2026-09-09T12:05:00Z",
            deviceUuid = phone,
        )
        // Even a peer that somehow published `theme` cannot change this device's theme.
        assertTrue(plan.isEmpty)
        assertEquals(emptyMap<String, String>(), plan.applied)
    }

    @Test
    fun valuesAreCopiedByteForByte() {
        // Formatting is not normalised anywhere in the path, so a value that survives a round trip
        // compares equal next cycle instead of looking like a fresh edit forever.
        val awkward = """{"b":1,"a":[2,3],"s":"  spaced  "}"""
        val plan = planSettingsSync(
            local = emptyMap(),
            merged = merged(SharedRecord.Setting("classes", awkward, "2026-09-09T12:00:00Z", phone)),
            applied = emptyMap(),
            now = "2026-09-09T12:05:00Z",
            deviceUuid = tablet,
        )
        assertEquals(awkward, plan.valuesToApply["classes"])
        assertEquals(awkward, plan.applied["classes"])
    }
}
