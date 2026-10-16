package io.github.eelnicoo.pocketcode

import io.github.eelnicoo.pocketcode.api.ClaudeClient
import io.github.eelnicoo.pocketcode.api.History
import io.github.eelnicoo.pocketcode.api.StreamAssembler
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {
    private fun j(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject
    private val user = j("""{"role":"user","content":[{"type":"text","text":"hola"}]}""")

    @Test fun appendUserMergesConsecutiveHumanTurns() {
        val h = History.appendUser(listOf(user), listOf(History.textBlock("otra")))
        assertEquals(1, h.size)
        assertEquals(2, History.blocks(h[0]).size)
    }

    @Test fun repairAnswersDanglingToolUse() {
        val a = j("""{"role":"assistant","content":[{"type":"tool_use","id":"t1","name":"x","input":{}}]}""")
        val h = History.repair(listOf(user, a), "parado")
        assertEquals(3, h.size)
        val r = History.blocks(h[2]).single()
        assertEquals("tool_result", History.type(r))
        assertEquals("parado", History.toolResultText(r))
    }

    @Test fun retryTruncatesToLastHumanTurn() {
        val a = j("""{"role":"assistant","content":[{"type":"text","text":"r"}]}""")
        assertEquals(listOf(user), History.truncateForRetry(listOf(user, a)))
    }

    @Test fun fallbackEchoRuleDropsDeclinedInternals() {
        val content = listOf(
            j("""{"type":"thinking","thinking":"x","signature":"s"}"""),
            j("""{"type":"text","text":"parcial"}"""),
            j("""{"type":"tool_use","id":"t","name":"x","input":{}}"""),
            j("""{"type":"fallback","from":{"model":"a"},"to":{"model":"b"}}"""),
            j("""{"type":"text","text":"final"}"""),
        )
        val out = History.sanitizeAssistant(content)
        assertEquals(listOf("text", "text"), out.map { History.type(it) })
    }

    @Test fun assemblerKeepsSignaturesAndToolInput() {
        val asm = StreamAssembler()
        listOf(
            """{"type":"message_start","message":{"model":"m"}}""",
            """{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"hmm"}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"SIG"}}""",
            """{"type":"content_block_stop","index":0}""",
            """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"t1","name":"read_file","input":{}}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"path\":"}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"\"a.txt\"}"}}""",
            """{"type":"content_block_stop","index":1}""",
            """{"type":"message_delta","delta":{"stop_reason":"tool_use"}}""",
            """{"type":"message_stop"}""",
        ).forEach { asm.handle(it) }
        assertTrue(asm.done)
        assertEquals("tool_use", asm.stopReason)
        val c = asm.finalContent()
        assertEquals("SIG", History.str(c[0], "signature"))
        assertEquals("hmm", History.str(c[0], "thinking"))
        assertEquals("a.txt", History.str(c[1]["input"]!!.jsonObject, "path"))
        assertTrue(asm.invalidToolInputs.isEmpty())
    }

    @Test fun unsignedThinkingIsDroppedFromFinalContent() {
        val asm = StreamAssembler()
        asm.handle("""{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}""")
        asm.handle("""{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"x"}}""")
        assertTrue(asm.finalContent().isEmpty())
    }

    @Test fun titles() {
        assertEquals("Crear app de notas", ClaudeClient.cleanTitle("\"Crear app de notas.\"\n"))
        assertNull(ClaudeClient.cleanTitle("   "))
        assertEquals("Nuevo chat", History.fallbackTitle(" "))
        assertTrue(History.fallbackTitle("a".repeat(100)).endsWith("…"))
    }

    @Test fun withoutThinkingKeepsAssistantTurnsNonEmpty() {
        val a = j("""{"role":"assistant","content":[{"type":"thinking","thinking":"x","signature":"s"}]}""")
        val b = j("""{"role":"assistant","content":[{"type":"thinking","thinking":"x","signature":"s"},{"type":"text","text":"hola"}]}""")
        val out = History.withoutThinking(listOf(user, a, user, b))
        assertEquals(listOf("text"), History.blocks(out[1]).map { History.type(it) })
        assertEquals(listOf("text"), History.blocks(out[3]).map { History.type(it) })
        assertEquals(user, out[0])
    }
}
