package net.activitywatch.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [planDecisionSync] -- what one cycle carries between the server's datastore and the shared folder
 * (roadmap 4.2).
 *
 * The properties worth holding on to are that it needs **no memory** and is therefore idempotent,
 * and that it never writes into a file it does not own (**R20**). Both are tested directly below
 * rather than left as a comment, because both are easy to break by "improving" the function later.
 */
class DecisionSyncTest {

    private val phone = "1111-aaaa"
    private val tablet = "2222-bbbb"

    private fun decision(id: String, author: String) =
        """{"id":"$id","type":"decision","created_at":"2026-09-10T11:00:00Z","created_by":"$author",""" +
            """"window":{"start":"2026-09-10T10:00:00Z","end":"2026-09-10T11:00:00Z"},""" +
            """"signature":{"participants":[]},""" +
            """"resolution":{"outcome":"foreground","foreground":{"device_role":"phone","app":"YouTube"}},""" +
            """"scope":"once"}"""

    private fun tombstone(id: String, author: String, revokes: String) =
        """{"id":"$id","type":"tombstone","created_at":"2026-09-10T12:00:00Z",""" +
            """"created_by":"$author","revokes":"$revokes"}"""

    @Test
    fun publishesOurOwnDecisionsThatTheFolderDoesNotHaveYet() {
        val plan = planDecisionSync(
            localLines = listOf(decision("d_1", phone)),
            sharedLines = emptyList(),
            deviceUuid = phone,
        )
        assertEquals(listOf(decision("d_1", phone)), plan.linesToPublish)
        assertTrue(plan.linesToImport.isEmpty())
    }

    @Test
    fun neverWritesAPeersDecisionIntoOurFile() {
        // R20: one writer per file. A peer's decision reached us through an earlier import; the
        // copy that belongs in the shared folder is the one in *its* author's file.
        val plan = planDecisionSync(
            localLines = listOf(decision("d_1", tablet)),
            sharedLines = emptyList(),
            deviceUuid = phone,
        )
        assertTrue(plan.linesToPublish.isEmpty())
    }

    @Test
    fun importsWhatTheFolderHasAndWeDoNot() {
        val peer = decision("d_2", tablet)
        val plan = planDecisionSync(
            localLines = listOf(decision("d_1", phone)),
            sharedLines = listOf(decision("d_1", phone), peer),
            deviceUuid = phone,
        )
        assertEquals(listOf(peer), plan.linesToImport)
        assertTrue("d_1 is already published", plan.linesToPublish.isEmpty())
    }

    @Test
    fun tombstonesTravelTheSameWayDecisionsDo() {
        // R12's undo has to reach the other device, and a tombstone routinely lives in a different
        // file from the decision it revokes (05 §4.1).
        val t = tombstone("t_1", phone, "d_9")
        val plan = planDecisionSync(listOf(t), emptyList(), phone)
        assertEquals(listOf(t), plan.linesToPublish)
    }

    @Test
    fun aSecondRunMovesNothing() {
        // The property that lets this be called every cycle without remembering anything.
        val mine = decision("d_1", phone)
        val theirs = decision("d_2", tablet)
        val first = planDecisionSync(listOf(mine), listOf(theirs), phone)
        assertEquals(listOf(mine), first.linesToPublish)
        assertEquals(listOf(theirs), first.linesToImport)

        val after = planDecisionSync(listOf(mine, theirs), listOf(theirs, mine), phone)
        assertTrue(after.isEmpty)
    }

    @Test
    fun aDuplicatedIdIsCarriedOnce() {
        val mine = decision("d_1", phone)
        // Same id, a byte different -- a line that took two routes here.
        val variant = mine.replace(""""scope":"once"""", """"scope": "once"""")
        val plan = planDecisionSync(listOf(mine, variant), emptyList(), phone)
        assertEquals(1, plan.linesToPublish.size)
    }

    @Test
    fun aLineNeitherSideUnderstandsStaysWhereItIs() {
        // 05 §8: a record from a newer build, or a half-written transfer. Not ours to move, and
        // certainly not ours to delete.
        val strange = """{"type":"prophecy","id":"p_1"}"""
        val plan = planDecisionSync(listOf(strange), listOf("not json at all"), phone)
        assertTrue(plan.isEmpty)
    }

    @Test
    fun settingsLinesAreNotDecisions() {
        // Both files are JSONL of SharedRecords and both are read through the same parser. Nothing
        // but the file name keeps them apart, so make sure the plan does too.
        val setting =
            """{"type":"setting","key":"startOfDay","value":"\"04:00\"","updated_at":"2026-09-10T11:00:00Z","updated_by":"$phone"}"""
        val plan = planDecisionSync(listOf(setting), listOf(setting), phone)
        assertTrue(plan.isEmpty)
    }
}
