package io.github.eelnicoo.pocketcode

import io.github.eelnicoo.pocketcode.api.History
import io.github.eelnicoo.pocketcode.chat.AssistantRow
import io.github.eelnicoo.pocketcode.chat.ChatRows
import io.github.eelnicoo.pocketcode.chat.TextPart
import io.github.eelnicoo.pocketcode.chat.ThinkingPart
import io.github.eelnicoo.pocketcode.chat.ToolPart
import io.github.eelnicoo.pocketcode.chat.ToolState
import io.github.eelnicoo.pocketcode.chat.UserRow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRowsTest {
    private fun j(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    private val history = listOf(
        j("""{"role":"user","content":[{"type":"text","text":"<archivo nombre=\"a.txt\">\nx\n</archivo>"},{"type":"text","text":"hola"}]}"""),
        j("""{"role":"assistant","content":[{"type":"thinking","thinking":"pienso","signature":"s"},{"type":"text","text":"Voy."},{"type":"tool_use","id":"t1","name":"run_shell","input":{"command":"ls"}}]}"""),
        j("""{"role":"user","content":[{"type":"tool_result","tool_use_id":"t1","content":[{"type":"text","text":"ok"}]}]}"""),
        j("""{"role":"assistant","content":[{"type":"text","text":"Listo."}]}"""),
    )

    @Test fun groupsOneTurnIntoOneAssistantRow() {
        val rows = ChatRows.build(history, null, emptySet(), busy = false)
        assertEquals(2, rows.size)
        val u = rows[0] as UserRow
        assertEquals("hola", u.text)
        assertEquals(listOf("a.txt"), u.files)
        val a = rows[1] as AssistantRow
        assertTrue(a.isLast)
        assertFalse(a.streaming)
        assertTrue(a.parts[0] is ThinkingPart)
        val tool = a.parts[2] as ToolPart
        assertEquals(ToolState.DONE, tool.state)
        assertEquals("ok", tool.output)
        assertEquals("Voy.\n\nListo.", a.copyText)
    }

    @Test fun draftAndPendingStates() {
        val h = history + j("""{"role":"user","content":[{"type":"text","text":"más"}]}""")
        val pending = ChatRows.build(h, null, emptySet(), busy = true)
        val last = pending.last() as AssistantRow
        assertTrue(last.streaming)
        assertTrue(last.parts.isEmpty())

        val draft = listOf(j("""{"type":"tool_use","id":"t2","name":"write_file","input":{},"_partial_input":"{\"pa"}"""))
        val rows = ChatRows.build(h, draft, emptySet(), busy = true)
        val tool = (rows.last() as AssistantRow).parts.single() as ToolPart
        assertEquals(ToolState.WRITING, tool.state)
    }

    @Test fun unansweredToolIsInterruptedWhenIdle() {
        val h = history.take(2)
        val a = ChatRows.build(h, null, emptySet(), busy = false).last() as AssistantRow
        assertEquals(ToolState.INTERRUPTED, (a.parts[2] as ToolPart).state)
        assertTrue(a.parts[1] is TextPart)
    }

    @Test fun turnWithoutAnswerOffersRetry() {
        val h = history + j("""{"role":"user","content":[{"type":"text","text":"otra"}]}""")
        val last = ChatRows.build(h, null, emptySet(), busy = false).last() as AssistantRow
        assertTrue(last.incomplete)
        assertTrue(last.parts.isEmpty())
        val afterTools = ChatRows.build(history.take(3), null, emptySet(), busy = false).last() as AssistantRow
        assertTrue(afterTools.incomplete)
        val done = ChatRows.build(history, null, emptySet(), busy = false).last() as AssistantRow
        assertFalse(done.incomplete)
    }
}
