package net.activitywatch.android

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.random.Random

/**
 * The property that matters here is **order-independence** (R18): merging the same lines in a
 * different order must produce the same answer, because Syncthing delivers files in any order and
 * arbitrarily late (R23). Every merge test below therefore runs its input through
 * [assertOrderIndependent] rather than merging once in the order it was written.
 */
class SharedStoreTest {

    private val phone = "1111-aaaa"
    private val tablet = "2222-bbbb"

    private fun participants() = listOf(
        Participant("phone", phone, "com.google.android.youtube", "video"),
        Participant("tablet", tablet, "com.amazon.kindle", "reading"),
    )

    private fun decision(
        id: String,
        createdAt: String,
        createdBy: String,
        start: String = "2026-09-02T14:30:00Z",
        end: String = "2026-09-02T14:45:00Z",
        participants: List<Participant> = participants(),
        outcome: String = "foreground",
        scope: String = SCOPE_ALWAYS,
    ) = SharedRecord.Decision(
        id = id,
        createdAt = createdAt,
        createdBy = createdBy,
        window = TimeWindow(start, end),
        signature = Signature.of(participants),
        resolution = Resolution(
            outcome = outcome,
            foreground = ForegroundPick("tablet", "com.amazon.kindle"),
            label = null,
            deliberateBackground = listOf("com.google.android.youtube"),
        ),
        scope = scope,
    )

    private fun tombstone(id: String, revokes: String, createdBy: String) =
        SharedRecord.Tombstone(id, "2026-09-02T15:00:00Z", createdBy, revokes)

    private fun setting(key: String, value: String, at: String, by: String) =
        SharedRecord.Setting(key, value, at, by)

    /**
     * Assert that [merge] gives the same answer no matter what order the lines arrive in.
     *
     * A single shuffle would pass by luck often enough to be useless, so this walks a fixed set of
     * seeds -- fixed so a failure is reproducible rather than a once-a-month mystery in CI.
     */
    private fun <T> assertOrderIndependent(
        records: List<SharedRecord>,
        merge: (List<SharedRecord>) -> T,
    ): T {
        val expected = merge(records)
        for (seed in 1..50) {
            val shuffled = records.shuffled(Random(seed))
            assertEquals("order-dependent result for seed $seed", expected, merge(shuffled))
        }
        assertEquals("reversing the input changed the result", expected, merge(records.reversed()))
        return expected
    }

    // -- parsing -------------------------------------------------------------------------------

    @Test
    fun decision_roundTripsThroughOneLine() {
        val original = decision("d_1", "2026-09-02T14:47:11Z", phone)
        val line = original.toJsonLine()
        assertTrue("a JSONL line must not contain a newline", !line.contains('\n'))
        assertEquals(original, parseSharedLine(line))
    }

    @Test
    fun decision_writesTheDocumentedFieldNames() {
        val json = JSONObject(decision("d_1", "2026-09-02T14:47:11Z", phone).toJsonLine())
        assertEquals("d_1", json.getString("id"))
        assertEquals(RECORD_TYPE_DECISION, json.getString("type"))
        assertEquals("2026-09-02T14:47:11Z", json.getString("created_at"))
        assertEquals(phone, json.getString("created_by"))
        assertEquals("2026-09-02T14:30:00Z", json.getJSONObject("window").getString("start"))
        assertEquals(2, json.getJSONObject("signature").getJSONArray("participants").length())
        assertEquals("foreground", json.getJSONObject("resolution").getString("outcome"))
        assertEquals(
            "com.amazon.kindle",
            json.getJSONObject("resolution").getJSONObject("foreground").getString("app"),
        )
        assertEquals(SCOPE_ALWAYS, json.getString("scope"))
    }

    @Test
    fun tombstoneAndSetting_roundTrip() {
        val t = tombstone("t_1", "d_1", tablet)
        assertEquals(t, parseSharedLine(t.toJsonLine()))
        val s = setting("category.com.google.android.youtube", "fun", "2026-09-01T09:12:00Z", phone)
        assertEquals(s, parseSharedLine(s.toJsonLine()))
        assertEquals(RECORD_TYPE_SETTING, JSONObject(s.toJsonLine()).getString("type"))
    }

    @Test
    fun unknownLines_areKeptVerbatimNotDropped() {
        // 05_DATA_MODEL.md section 8: a newer device's line type is ignored, never destroyed.
        val future = """{"type":"annotation","id":"a_1","note":"written by a newer build"}"""
        assertEquals(SharedRecord.Unknown(future), parseSharedLine(future))
        // Not JSON at all -- a line truncated mid-transfer is not ours to delete either.
        assertEquals(SharedRecord.Unknown("{half a li"), parseSharedLine("{half a li"))
        // A decision without an id cannot be revoked or grouped, so it is kept, not interpreted.
        val idless = """{"type":"decision","window":{"start":"a","end":"b"}}"""
        assertEquals(SharedRecord.Unknown(idless), parseSharedLine(idless))
        // Neither is a tombstone that revokes nothing.
        val revokesNothing = """{"type":"tombstone","id":"t_9"}"""
        assertEquals(SharedRecord.Unknown(revokesNothing), parseSharedLine(revokesNothing))
        // Round-tripping an unknown line must not alter a byte of it.
        assertEquals(future, (parseSharedLine(future) as SharedRecord.Unknown).toJsonLine())
    }

    @Test
    fun blankLinesCarryNothing() {
        assertNull(parseSharedLine(""))
        assertNull(parseSharedLine("   "))
        assertEquals(emptyList<SharedRecord>(), parseSharedJsonl(null))
    }

    @Test
    fun jsonl_readsTheWholeFileIncludingCrlfAndATrailingNewline() {
        val text = buildString {
            append(decision("d_1", "2026-09-02T14:47:11Z", phone).toJsonLine()).append("\r\n")
            append("\n")
            append(tombstone("t_1", "d_1", tablet).toJsonLine()).append("\n")
        }
        val records = parseSharedJsonl(text)
        assertEquals(2, records.size)
        assertTrue(records[0] is SharedRecord.Decision)
        assertTrue(records[1] is SharedRecord.Tombstone)
    }

    // -- signatures ----------------------------------------------------------------------------

    @Test
    fun signature_isTheSameKeyWhicheverOrderItWasWrittenIn() {
        val forwards = Signature.of(participants())
        val backwards = Signature.of(participants().reversed())
        assertEquals(forwards.matchKey, backwards.matchKey)
        assertEquals(forwards, backwards)
    }

    @Test
    fun signature_matchesOnRoleNotUuid() {
        // A replaced phone keeps the rule alive (04_COMBINED_TIMELINE.md section 3); a different
        // app does not.
        val newPhone = participants().map {
            if (it.deviceRole == "phone") it.copy(deviceUuid = "3333-cccc") else it
        }
        assertEquals(Signature.of(participants()).matchKey, Signature.of(newPhone).matchKey)

        val otherApp = participants().map {
            if (it.deviceRole == "phone") it.copy(app = "com.netflix") else it
        }
        assertNotEquals(Signature.of(participants()).matchKey, Signature.of(otherApp).matchKey)
    }

    @Test
    fun signature_doesNotConflateDifferentSplitsOfTheSameText() {
        // Separators, not concatenation: {phone, a, bc} and {phone, ab, c} are different rules.
        val a = Signature.of(listOf(Participant("phone", phone, "a", "bc")))
        val b = Signature.of(listOf(Participant("phone", phone, "ab", "c")))
        assertNotEquals(a.matchKey, b.matchKey)
    }

    // -- decision merge ------------------------------------------------------------------------

    @Test
    fun merge_keepsTheNewestDecisionPerWindowAndSignature() {
        val older = decision("d_1", "2026-09-02T14:47:11Z", phone)
        val newer = decision("d_2", "2026-09-02T16:00:00Z", tablet)
        val merged = assertOrderIndependent(listOf(older, newer), ::mergeDecisions)
        assertEquals(listOf("d_2"), merged.map { it.id })
    }

    @Test
    fun merge_breaksTimestampTiesOnTheLowestAuthorUuid() {
        // The one rule that makes three devices agree without talking to each other.
        val fromTablet = decision("d_2", "2026-09-02T14:47:11Z", tablet)
        val fromPhone = decision("d_1", "2026-09-02T14:47:11Z", phone)
        val merged = assertOrderIndependent(listOf(fromTablet, fromPhone), ::mergeDecisions)
        assertEquals(listOf("d_1"), merged.map { it.id })
        assertEquals(phone, merged.single().createdBy)
    }

    @Test
    fun merge_breaksAnAuthorTieOnTheLowestId() {
        // Same device, same millisecond, same group: without this the winner would be whichever
        // line happened to be read first.
        val second = decision("d_2", "2026-09-02T14:47:11Z", phone)
        val first = decision("d_1", "2026-09-02T14:47:11Z", phone)
        val merged = assertOrderIndependent(listOf(second, first), ::mergeDecisions)
        assertEquals(listOf("d_1"), merged.map { it.id })
    }

    @Test
    fun merge_dropsWhatATombstoneRevokesEvenFromAnotherDevicesFile() {
        // The tombstone is written by the tablet; the decision it revokes lives in the phone's
        // file. Resolving that on disk is impossible, which is the point of merging at read time.
        val d = decision("d_1", "2026-09-02T14:47:11Z", phone)
        val t = tombstone("t_1", "d_1", tablet)
        val merged = assertOrderIndependent(listOf(d, t), ::mergeDecisions)
        assertEquals(emptyList<String>(), merged.map { it.id })
    }

    @Test
    fun merge_revokingTheNewerDecisionExposesTheOlderOne() {
        // Undo (R12) must put the previous answer back, not leave the window unresolved -- and it
        // must do so whichever order the three lines arrive in.
        val older = decision("d_1", "2026-09-02T14:47:11Z", phone)
        val newer = decision("d_2", "2026-09-02T16:00:00Z", tablet)
        val undo = tombstone("t_1", "d_2", tablet)
        val merged = assertOrderIndependent(listOf(older, newer, undo), ::mergeDecisions)
        assertEquals(listOf("d_1"), merged.map { it.id })
    }

    @Test
    fun merge_countsADuplicatedLineOnce() {
        // A hand-copied or restored file can carry the same line twice; it must not out-vote the
        // decision that actually won.
        val duplicated = decision("d_1", "2026-09-02T14:47:11Z", phone)
        val winner = decision("d_2", "2026-09-02T16:00:00Z", tablet)
        val merged = assertOrderIndependent(
            listOf(duplicated, winner, duplicated.copy()),
            ::mergeDecisions,
        )
        assertEquals(listOf("d_2"), merged.map { it.id })
    }

    @Test
    fun merge_keepsDifferentWindowsAndSignaturesApart() {
        val here = decision("d_1", "2026-09-02T14:47:11Z", phone)
        val laterWindow = decision(
            "d_2", "2026-09-02T14:47:11Z", phone,
            start = "2026-09-02T15:00:00Z", end = "2026-09-02T15:30:00Z",
        )
        val otherApps = decision(
            "d_3", "2026-09-02T14:47:11Z", phone,
            participants = listOf(
                Participant("phone", phone, "com.netflix", "video"),
                Participant("tablet", tablet, "com.amazon.kindle", "reading"),
            ),
        )
        val merged = assertOrderIndependent(listOf(here, laterWindow, otherApps), ::mergeDecisions)
        // Sorted by window then id, so the list itself is order-independent, not just its contents.
        assertEquals(listOf("d_1", "d_3", "d_2"), merged.map { it.id })
    }

    @Test
    fun merge_ignoresUnknownLinesWithoutLosingRealOnes() {
        val d = decision("d_1", "2026-09-02T14:47:11Z", phone)
        val noise = listOf(
            SharedRecord.Unknown("""{"type":"annotation","id":"a_1"}"""),
            SharedRecord.Unknown("garbage"),
            setting("category.x", "y", "2026-09-01T09:12:00Z", phone),
        )
        val merged = assertOrderIndependent(noise + d, ::mergeDecisions)
        assertEquals(listOf("d_1"), merged.map { it.id })
    }

    @Test
    fun merge_prefersAParseableTimestampOverABrokenOne() {
        // A device with a broken clock format must not silently win every window it touches.
        val broken = decision("d_1", "yesterday", phone)
        val real = decision("d_2", "2020-01-01T00:00:00Z", tablet)
        val merged = assertOrderIndependent(listOf(broken, real), ::mergeDecisions)
        assertEquals(listOf("d_2"), merged.map { it.id })
    }

    @Test
    fun merge_ofNothingIsNothing() {
        assertEquals(emptyList<SharedRecord.Decision>(), mergeDecisions(emptyList()))
    }

    // -- settings merge ------------------------------------------------------------------------

    @Test
    fun settings_lastWriteWinsPerKey() {
        val records = listOf(
            setting("category.com.google.android.youtube", "video", "2026-09-01T09:12:00Z", phone),
            setting("category.com.google.android.youtube", "fun", "2026-09-03T10:00:00Z", tablet),
            setting("label.work", "Work", "2026-09-02T08:00:00Z", phone),
        )
        val values = assertOrderIndependent(records, ::effectiveSettingValues)
        assertEquals("fun", values["category.com.google.android.youtube"])
        assertEquals("Work", values["label.work"])
    }

    @Test
    fun settings_breakTiesOnTheLowestAuthorUuid() {
        // Deliberately the same rule as decisions (R18/R29): two devices renaming one category in
        // the same second must not disagree afterwards.
        val records = listOf(
            setting("category.x", "from-tablet", "2026-09-03T10:00:00Z", tablet),
            setting("category.x", "from-phone", "2026-09-03T10:00:00Z", phone),
        )
        val values = assertOrderIndependent(records, ::effectiveSettingValues)
        assertEquals("from-phone", values["category.x"])
    }

    @Test
    fun settings_areListedInKeyOrderAndCarryProvenance() {
        val records = listOf(
            setting("label.work", "Work", "2026-09-02T08:00:00Z", phone),
            setting("category.x", "fun", "2026-09-03T10:00:00Z", tablet),
        )
        val effective = assertOrderIndependent(records, ::effectiveSettings)
        assertEquals(listOf("category.x", "label.work"), effective.keys.toList())
        assertEquals(tablet, effective["category.x"]?.updatedBy)
    }

    @Test
    fun settings_ignoreDecisionsAndUnknownLinesSharingTheStore() {
        val records = listOf(
            decision("d_1", "2026-09-02T14:47:11Z", phone),
            SharedRecord.Unknown("""{"type":"preference","key":"category.x"}"""),
            setting("category.x", "fun", "2026-09-03T10:00:00Z", phone),
        )
        assertEquals(mapOf("category.x" to "fun"), effectiveSettingValues(records))
    }

    // -- identifiers ---------------------------------------------------------------------------

    @Test
    fun ulid_is26CrockfordCharacters() {
        val id = Ulid.generate(Instant.parse("2026-09-09T12:00:00Z"), Random(7))
        assertEquals(26, id.length)
        assertTrue(id, id.all { it in "0123456789ABCDEFGHJKMNPQRSTVWXYZ" })
        assertTrue(Ulid.decisionId().startsWith("d_"))
        assertTrue(Ulid.tombstoneId().startsWith("t_"))
    }

    @Test
    fun ulid_sortsChronologicallyAsPlainText() {
        // What earns a ULID over a UUID: an append-only log reads in order without an index.
        val earlier = Ulid.generate(Instant.parse("2026-09-09T12:00:00Z"), Random(1))
        val later = Ulid.generate(Instant.parse("2026-09-09T12:00:01Z"), Random(2))
        assertTrue("$earlier should sort before $later", earlier < later)
    }

    @Test
    fun ulid_differsWithinTheSameMillisecond() {
        val at = Instant.parse("2026-09-09T12:00:00Z")
        val ids = (1..100).map { Ulid.generate(at) }.toSet()
        assertEquals("80 random bits should not collide in 100 draws", 100, ids.size)
        // The timestamp half is shared; only the random half differs.
        assertEquals(1, ids.map { it.take(10) }.toSet().size)
    }
}
