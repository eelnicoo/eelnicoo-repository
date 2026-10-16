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
        assertEquals(2, (b[4] as MdBlock.ListItem).level)
        assertTrue((b[5] as MdBlock.ListItem).ordered)
        assertTrue(b[6] is MdBlock.Quote)
        assertEquals(MdBlock.Rule, b[7])
        val t = b[8] as MdBlock.Table
        assertEquals(listOf(Align.LEFT, Align.RIGHT), t.aligns)
        assertEquals("x|y", MarkdownParser.plain(t.rows[0][1]))
        assertEquals(MdBlock.CodeBlock("kotlin", "val x = 1", true), b[9])
        assertEquals(MdBlock.CodeBlock("py", "print(1)", false), b[10])
    }
}
