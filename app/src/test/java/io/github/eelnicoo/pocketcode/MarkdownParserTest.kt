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
}
