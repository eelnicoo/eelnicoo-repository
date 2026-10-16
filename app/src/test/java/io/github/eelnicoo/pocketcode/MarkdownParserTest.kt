package io.github.eelnicoo.pocketcode

import io.github.eelnicoo.pocketcode.markdown.Align
import io.github.eelnicoo.pocketcode.markdown.Inline
import io.github.eelnicoo.pocketcode.markdown.MarkdownParser
import io.github.eelnicoo.pocketcode.markdown.MdBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test fun boldWrappingInlineCode() {
        // The bug from the screenshot: **`hola.txt`** must be bold code, not literal asterisks.
        val inl = MarkdownParser.parseInline("Creé **`hola.txt`** en `/sdcard/Download/`")
        assertEquals(Inline.Text("Creé "), inl[0])
        assertEquals(Inline.Bold(listOf(Inline.Code("hola.txt"))), inl[1])
        assertEquals(Inline.Text(" en "), inl[2])
        assertEquals(Inline.Code("/sdcard/Download/"), inl[3])
    }

    @Test fun italicBoldStrikeAndLinks() {
        val inl = MarkdownParser.parseInline("*a* __b__ ~~c~~ [d](https://x.io) https://y.io/p.")
        assertEquals(Inline.Italic(listOf(Inline.Text("a"))), inl[0])
        assertEquals(Inline.Bold(listOf(Inline.Text("b"))), inl[2])
        assertEquals(Inline.Strike(listOf(Inline.Text("c"))), inl[4])
        assertEquals(Inline.Link(listOf(Inline.Text("d")), "https://x.io"), inl[6])
        assertEquals(Inline.Link(listOf(Inline.Text("https://y.io/p")), "https://y.io/p"), inl[8])
        assertEquals(Inline.Text("."), inl[9])
    }

    @Test fun snakeCaseAndMathAreNotEmphasis() {
        assertEquals("my_var_name", MarkdownParser.plain(MarkdownParser.parseInline("my_var_name")))
        assertEquals(listOf(Inline.Text("2 * 3 * 4")), MarkdownParser.parseInline("2 * 3 * 4"))
    }

    @Test fun unclosedDelimitersStayLiteral() {
        assertEquals("**hola", MarkdownParser.plain(MarkdownParser.parseInline("**hola")))
        assertEquals("`abc", MarkdownParser.plain(MarkdownParser.parseInline("`abc")))
    }

    @Test fun nestedItalicInsideBold() {
        val inl = MarkdownParser.parseInline("**muy *importante* ya**")
        val bold = inl.single() as Inline.Bold
        assertTrue(bold.children.any { it is Inline.Italic })
    }

    @Test fun blocks() {
        val md = """
            # Título
            Texto con
            dos líneas.

            - uno
              sigue
            - [x] hecho
                - anidado
            1. primero

            > cita

            ---
            | a | b |
            |:--|--:|
            | 1 | `x|y` |
            ```kotlin
            val x = 1
            ```
            ```py
            print(1)
        """.trimIndent()
        val b = MarkdownParser.parse(md)
        assertEquals(MdBlock.Heading(1, listOf(Inline.Text("Título"))), b[0])
        val p = b[1] as MdBlock.Paragraph
        assertTrue(p.inlines.contains(Inline.LineBreak))
        val li = b[2] as MdBlock.ListItem
        assertEquals("uno\nsigue", MarkdownParser.plain(li.inlines))
        assertEquals(true, (b[3] as MdBlock.ListItem).task)
        assertEquals(1, (b[4] as MdBlock.ListItem).level)
        assertTrue((b[5] as MdBlock.ListItem).ordered)
        assertTrue(b[6] is MdBlock.Quote)
        assertEquals(MdBlock.Rule, b[7])
        val t = b[8] as MdBlock.Table
        assertEquals(listOf(Align.LEFT, Align.RIGHT), t.aligns)
        assertEquals("x|y", MarkdownParser.plain(t.rows[0][1]))
        assertEquals(MdBlock.CodeBlock("kotlin", "val x = 1", true), b[9])
        assertEquals(MdBlock.CodeBlock("py", "print(1)", false), b[10])
    }

    @Test fun indentedFenceInsideList() {
        val b = MarkdownParser.parse("10. Configura:\n    ```bash\n    npm install\n\n    npm run build\n    ```\n11. Listo")
        assertEquals(10, (b[0] as MdBlock.ListItem).number)
        assertEquals(MdBlock.CodeBlock("bash", "npm install\n\nnpm run build", true), b[1])
        assertEquals(11, (b[2] as MdBlock.ListItem).number)
    }

    @Test fun inlineTripleBackticksDoNotOpenAFence() {
        val b = MarkdownParser.parse("```npm install``` y luego:\n\n```bash\nnpm run build\n```")
        val p = b[0] as MdBlock.Paragraph
        assertEquals(Inline.Code("npm install"), p.inlines[0])
        assertEquals(MdBlock.CodeBlock("bash", "npm run build", true), b[1])
    }

    @Test fun headingKeepsTrailingHashWithoutSpace() {
        assertEquals(MdBlock.Heading(2, listOf(Inline.Text("Learning C#"))), MarkdownParser.parse("## Learning C#")[0])
        assertEquals(MdBlock.Heading(1, listOf(Inline.Text("Title"))), MarkdownParser.parse("# Title ##")[0])
        assertTrue(MarkdownParser.parse("#hashtag")[0] is MdBlock.Paragraph)
    }

    @Test fun tripleStarIsBoldItalic() {
        assertEquals(Inline.Bold(listOf(Inline.Italic(listOf(Inline.Text("a"))))), MarkdownParser.parseInline("***a*** b")[0])
        val inl = MarkdownParser.parseInline("**bold *italic***")
        val bold = inl.single() as Inline.Bold
        assertEquals(Inline.Text("bold "), bold.children[0])
        assertEquals(Inline.Italic(listOf(Inline.Text("italic"))), bold.children[1])
    }

    @Test fun linkWithParenthesesInUrl() {
        val inl = MarkdownParser.parseInline("Mira [Kotlin](https://en.wikipedia.org/wiki/Kotlin_(programming_language)) ya")
        assertEquals(Inline.Link(listOf(Inline.Text("Kotlin")), "https://en.wikipedia.org/wiki/Kotlin_(programming_language)"), inl[1])
        assertEquals(Inline.Text(" ya"), inl[2])
    }

    @Test fun nestedListLevelsFollowIndentStack() {
        val b = MarkdownParser.parse("- a\n    - b\n        - c\n- d")
        assertEquals(listOf(0, 1, 2, 0), b.map { (it as MdBlock.ListItem).level })
    }

    @Test fun unknownLanguagesAreNotHighlighted() {
        val h = io.github.eelnicoo.pocketcode.markdown.CodeHighlighter
        assertTrue(h.highlight("Error: Could not find class Main", "").isEmpty())
        assertTrue(h.highlight("git clone https://github.com/x/y", "text").isEmpty())
        val bash = h.highlight("echo ${'$'}{#arr[@]} items # comentario", "bash")
        val comments = bash.filter { it.kind == io.github.eelnicoo.pocketcode.markdown.TokenKind.COMMENT }
        assertEquals(1, comments.size)
    }

    @Test fun italicWrappingBoldAndSplitTriple() {
        val a = MarkdownParser.parseInline("*italic **bold***").single() as Inline.Italic
        assertEquals(Inline.Text("italic "), a.children[0])
        assertEquals(Inline.Bold(listOf(Inline.Text("bold"))), a.children[1])
        val b = MarkdownParser.parseInline("***Nota:** algo*").single() as Inline.Italic
        assertEquals(Inline.Bold(listOf(Inline.Text("Nota:"))), b.children[0])
        assertEquals(Inline.Text(" algo"), b.children[1])
        val c = MarkdownParser.parseInline("***a* b**").single() as Inline.Bold
        assertEquals(Inline.Italic(listOf(Inline.Text("a"))), c.children[0])
    }

    @Test fun indentedTextAfterInItemFenceKeepsNesting() {
        val md = "- a\n    - b\n        ```\n        x\n        ```\n        sigue\n    - c"
        val levels = MarkdownParser.parse(md).filterIsInstance<MdBlock.ListItem>().map { it.level }
        assertEquals(listOf(0, 1, 1), levels)
    }

    @Test fun degenerateInputsDoNotCrash() {
        for (t in listOf("***", "**", "*", "[a](", "[a]", "```", "~~~", "\t- x", "| a |\n|---|", "> ", "#", "1.", "- ", "`", "__", "___x___")) {
            MarkdownParser.parse(t)
            MarkdownParser.parseInline(t)
        }
    }

    @Test fun italicAroundTwoBoldSpansAndMixedTriples() {
        val a = MarkdownParser.parseInline("*Usa **git add** y luego **git commit***").single() as Inline.Italic
        assertEquals(Inline.Bold(listOf(Inline.Text("git add"))), a.children[1])
        assertEquals(Inline.Bold(listOf(Inline.Text("git commit"))), a.children.last())
        val b = MarkdownParser.parseInline("***Nota:** algo* y ***otra***")
        assertEquals(Inline.Italic(listOf(Inline.Bold(listOf(Inline.Text("Nota:"))), Inline.Text(" algo"))), b[0])
        assertEquals(Inline.Bold(listOf(Inline.Italic(listOf(Inline.Text("otra"))))), b.last())
    }

    @Test fun snakeCaseSurvivesTripleUnderscore() {
        assertEquals("_Nota ver my_var_name", MarkdownParser.plain(MarkdownParser.parseInline("___Nota__ ver my_var_name")))
        assertEquals(listOf(Inline.Text("5 * 3 = 15 y a*b")), MarkdownParser.parseInline("5 * 3 = 15 y a*b"))
    }

    @Test fun unpairedDoubleStarInsideItalic() {
        val inl = MarkdownParser.parseInline("*Nota: el parámetro **kwargs es opcional* y **no** se valida.")
        assertEquals(Inline.Italic(listOf(Inline.Text("Nota: el parámetro **kwargs es opcional"))), inl[0])
        assertEquals(Inline.Text(" y "), inl[1])
        assertEquals(Inline.Bold(listOf(Inline.Text("no"))), inl[2])
    }

    @Test fun boldUnitsWithInnerDelimitersAreKept() {
        val a = MarkdownParser.parseInline("En Python, x ** 2 eleva al cuadrado; el patrón es **tests/*.py**.")
        assertTrue(a.contains(Inline.Bold(listOf(Inline.Text("tests/*.py")))))
        assertTrue(MarkdownParser.parseInline("Calcula x ** 2 y **x*y**.").contains(Inline.Bold(listOf(Inline.Text("x*y")))))
        val b = MarkdownParser.parseInline("_Nota: __max_tokens__ es obligatorio._").single() as Inline.Italic
        assertEquals(Inline.Bold(listOf(Inline.Text("max_tokens"))), b.children[1])
        val c = MarkdownParser.parseInline("*Edit **src/*.kt** only*").single() as Inline.Italic
        assertEquals(Inline.Bold(listOf(Inline.Text("src/*.kt"))), c.children[1])
        val d = MarkdownParser.parseInline("_Nota: la variable __my_var__ es global_").single() as Inline.Italic
        assertEquals(Inline.Bold(listOf(Inline.Text("my_var"))), d.children[1])
    }

    @Test fun adversarialDelimiterInputsStayFast() {
        val inputs = listOf(
            " *x".repeat(800) + " " + "**a * * ".repeat(800) + " * b**",
            "*".repeat(4000),
            "_a".repeat(3000),
            "**a ".repeat(2000),
        )
        for (t in inputs) {
            val start = System.nanoTime()
            MarkdownParser.parseInline(t)
            MarkdownParser.parse(t)
            val ms = (System.nanoTime() - start) / 1_000_000
            assertTrue("took ${'$'}ms ms for input of length ${'$'}{t.length}", ms < 1500)
        }
    }

    @Test fun longRuleLinesAndHeadingsAreHandledWithoutRegexRecursion() {
        assertEquals(MdBlock.Rule, MarkdownParser.parse("-".repeat(20000))[0])
        assertEquals(MdBlock.Rule, MarkdownParser.parse("* ".repeat(10000))[0])
        assertEquals(MdBlock.Rule, MarkdownParser.parse("___")[0])
        assertTrue(MarkdownParser.parse("--x")[0] is MdBlock.Paragraph)
        assertEquals(MdBlock.Heading(3, listOf(Inline.Text("A"))), MarkdownParser.parse("### A ###")[0])
        assertTrue(MarkdownParser.parse("####### siete")[0] is MdBlock.Paragraph)
        val wide = "|" + "a|".repeat(3000) + "\n|" + "-|".repeat(3000)
        assertTrue(MarkdownParser.parse(wide)[0] is MdBlock.Table)
    }

    @Test fun highlighterSurvivesHugeLiterals() {
        val h = io.github.eelnicoo.pocketcode.markdown.CodeHighlighter
        val big = "val s = \"" + "a".repeat(200_000) + "\""
        assertTrue(h.highlight(big, "kotlin").any { it.kind == io.github.eelnicoo.pocketcode.markdown.TokenKind.STRING })
        h.highlight("x = \"" + "\\\\n".repeat(30_000) + "\"", "python")
        h.highlight("{\"" + "k".repeat(100_000) + "\": 1}", "json")
    }

    @Test fun spacedPowerOperatorDoesNotOpenItalic() {
        val a = MarkdownParser.parseInline("En Python, x ** 2 es el cuadrado y **2 * 3** es 6; *ojo* con la precedencia, **siempre**.")
        assertEquals(Inline.Text("En Python, x ** 2 es el cuadrado y "), a[0])
        assertEquals(Inline.Bold(listOf(Inline.Text("2 * 3"))), a[1])
        assertTrue(a.contains(Inline.Italic(listOf(Inline.Text("ojo")))))
        assertTrue(a.contains(Inline.Bold(listOf(Inline.Text("siempre")))))
        val b = MarkdownParser.parseInline("Calcula x ** 2 y luego *redondea*.")
        assertTrue(b.contains(Inline.Italic(listOf(Inline.Text("redondea")))))
    }

    @Test fun codeSpansAndEscapesInsideBoldDoNotCloseTheItalic() {
        val a = MarkdownParser.parseInline("*Recuerda: **`char* p` y `int* q`** declaran punteros* y **`*p`** los desreferencia.")
        val it = a[0] as Inline.Italic
        assertEquals(Inline.Bold(listOf(Inline.Code("char* p"), Inline.Text(" y "), Inline.Code("int* q"))), it.children[1])
        assertEquals(Inline.Bold(listOf(Inline.Code("*p"))), a[2])
        val b = MarkdownParser.parseInline("*Nota: **5 \\* 3 \\* 2** es 30.* Ahora **súmale 2**.")
        assertEquals(Inline.Italic(listOf(Inline.Text("Nota: "), Inline.Bold(listOf(Inline.Text("5 * 3 * 2"))), Inline.Text(" es 30."))), b[0])
    }

    @Test fun longParagraphKeepsEmphasisLineByLine() {
        val md = (1..80).joinToString("\n") { "**`VAR_NAME_${'$'}it`** — URL de *conexión* para el servicio_${'$'}it." }
        val inl = MarkdownParser.parseInline(md)
        assertEquals(80, inl.count { it is Inline.Bold })
        assertEquals(80, inl.count { it is Inline.Italic })
        assertEquals(79, inl.count { it == Inline.LineBreak })
    }

    @Test fun highlighterKeepsUnclosedQuotesContained() {
        val h = io.github.eelnicoo.pocketcode.markdown.CodeHighlighter
        val kind = io.github.eelnicoo.pocketcode.markdown.TokenKind.STRING
        fun strings(code: String, lang: String) = h.highlight(code, lang).filter { it.kind == kind }.map { code.substring(it.start, it.end) }

        // Apostrophes in XML text content are not strings; tags after them still colour.
        val xml = "<resources>\n  <string name=\"error\">Can't reach the server</string>\n  <string name=\"retry\">Retry</string>\n</resources>"
        assertEquals(listOf("\"error\"", "\"retry\""), strings(xml, "xml"))
        assertEquals(6, h.highlight(xml, "xml").count { it.kind == io.github.eelnicoo.pocketcode.markdown.TokenKind.TAG && xml[it.start] == '<' })
        assertEquals(listOf("\"match_parent\""), strings("android:layout_width=\"match_parent\"", "xml"))
        // A lone quote (a Rust lifetime) does not swallow the rest of the line.
        assertEquals(listOf("\"pocket\""), strings("const NAME: &'static str = \"pocket\";", "rust"))
        // Go raw strings have no escapes.
        val go = "p := strings.ReplaceAll(`C:\\Users\\`, `\\`, `/`)\n// done\nvar x = 42"
        assertEquals(listOf("`C:\\Users\\`", "`\\`", "`/`"), strings(go, "go"))
        assertTrue(h.highlight(go, "go").any { it.kind == io.github.eelnicoo.pocketcode.markdown.TokenKind.COMMENT })
        // Python docstrings with single quotes.
        assertEquals(listOf("'''Doc don't'''"), strings("def f():\n    '''Doc don't'''\n    pass", "python"))
    }

    @Test fun highlighterStaysLinearOnUnclosedQuotes() {
        val h = io.github.eelnicoo.pocketcode.markdown.CodeHighlighter
        val inputs = listOf(
            "'\\'".repeat(60_000),
            "x = \"\\\"".repeat(40_000),
            "`\\`".repeat(40_000) + "`",
            "<a b='" + "\\'".repeat(60_000),
        )
        for ((k, t) in inputs.withIndex()) {
            val start = System.nanoTime()
            h.highlight(t, if (k == 3) "xml" else "js")
            val ms = (System.nanoTime() - start) / 1_000_000
            assertTrue("took ${'$'}ms ms for input ${'$'}k", ms < 1500)
        }
    }
}
