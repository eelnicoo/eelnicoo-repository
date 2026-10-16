package io.github.eelnicoo.pocketcode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eelnicoo.pocketcode.markdown.Align
import io.github.eelnicoo.pocketcode.markdown.CodeHighlighter
import io.github.eelnicoo.pocketcode.markdown.Inline
import io.github.eelnicoo.pocketcode.markdown.MarkdownParser
import io.github.eelnicoo.pocketcode.markdown.MdBlock
import io.github.eelnicoo.pocketcode.markdown.TokenKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Renders a Markdown string the way Claude's replies look. */
@Composable
fun MarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = LocalClaude.current.text,
    fontFamily: FontFamily = LocalReplyFont.current,
    fontSize: TextUnit = 16.sp,
) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    val base = TextStyle(color = color, fontFamily = fontFamily, fontSize = fontSize, lineHeight = fontSize * 1.55f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        var i = 0
        while (i < blocks.size) {
            val b = blocks[i]
            if (b is MdBlock.ListItem) {
                // Consecutive list items render as one tight group.
                val group = ArrayList<MdBlock.ListItem>()
                while (i < blocks.size && blocks[i] is MdBlock.ListItem) { group.add(blocks[i] as MdBlock.ListItem); i++ }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { group.forEach { ListItemView(it, base) } }
                continue
            }
            BlockView(b, base)
            i++
        }
    }
}

@Composable
private fun BlockView(b: MdBlock, base: TextStyle) {
    val c = LocalClaude.current
    when (b) {
        is MdBlock.Paragraph -> Text(rememberInline(b.inlines), style = base)
        is MdBlock.Heading -> {
            val size = when (b.level) { 1 -> 22.sp; 2 -> 19.sp; 3 -> 17.sp; else -> 16.sp }
            Text(
                rememberInline(b.inlines),
                style = base.copy(fontSize = size, lineHeight = size * 1.35f, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        is MdBlock.CodeBlock -> CodeBlockView(b.lang, b.code)
        is MdBlock.Quote -> {
            val bar = c.border
            Column(
                Modifier
                    .fillMaxWidth()
                    .drawBehind { drawRect(bar, size = Size(3.dp.toPx(), size.height)) }
                    .padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                b.blocks.forEach { BlockView(it, base.copy(color = c.textSecondary)) }
            }
        }
        is MdBlock.ListItem -> ListItemView(b, base)
        is MdBlock.Table -> TableView(b, base)
        MdBlock.Rule -> HorizontalDivider(color = c.border, modifier = Modifier.padding(vertical = 6.dp))
    }
}

@Composable
private fun ListItemView(item: MdBlock.ListItem, base: TextStyle) {
    val c = LocalClaude.current
    Row(Modifier.padding(start = (item.level * 18).dp)) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.TopStart) {
            when {
                item.task != null -> Icon(
                    if (item.task) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint = if (item.task) c.accent else c.textTertiary,
                    modifier = Modifier.padding(top = 3.dp).size(18.dp),
                )
                item.ordered -> Text("${item.number}.", style = base.copy(color = c.textSecondary))
                else -> Text(if (item.level == 0) "•" else "◦", style = base.copy(color = c.textSecondary))
            }
        }
        Text(rememberInline(item.inlines), style = base, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TableView(t: MdBlock.Table, base: TextStyle) {
    val c = LocalClaude.current
    // Column widths estimated from the longest cell text, clamped to a sane range.
    val widths = remember(t) {
        List(t.header.size) { col ->
            val longest = (listOf(t.header[col]) + t.rows.map { it[col] })
                .maxOf { MarkdownParser.plain(it).lines().maxOfOrNull { l -> l.length } ?: 0 }
            (longest * 8 + 24).coerceIn(72, 280)
        }
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        Column(Modifier.clip(shape).border(1.dp, c.border, shape)) {
            TableRow(t.header, widths, t.aligns, base.copy(fontWeight = FontWeight.SemiBold), c.codeHeader)
            t.rows.forEachIndexed { r, row ->
                HorizontalDivider(color = c.border)
                TableRow(row, widths, t.aligns, base, if (r % 2 == 1) c.codeBg.copy(alpha = 0.5f) else Color.Transparent)
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<List<Inline>>, widths: List<Int>, aligns: List<Align>, style: TextStyle, bg: Color) {
    Row(Modifier.background(bg)) {
        cells.forEachIndexed { k, cell ->
            Text(
                rememberInline(cell),
                style = style.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = when (aligns.getOrElse(k) { Align.LEFT }) {
                        Align.CENTER -> TextAlign.Center
                        Align.RIGHT -> TextAlign.End
                        Align.LEFT -> TextAlign.Start
                    },
                ),
                modifier = Modifier.width(widths.getOrElse(k) { 120 }.dp).padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun CodeBlockView(lang: String, code: String) {
    val c = LocalClaude.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    val highlighted = remember(code, lang, c) { highlight(code, lang, c) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.codeBg)
            .border(1.dp, c.border, shape),
    ) {
        Row(
            Modifier.fillMaxWidth().background(c.codeHeader).padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(lang.ifBlank { "código" }, fontSize = 12.sp, color = c.textTertiary, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                        scope.launch { delay(1800); copied = false }
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (copied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    tint = c.textTertiary,
                    modifier = Modifier.size(14.dp),
                )
                Text(if (copied) " Copiado" else " Copiar", fontSize = 12.sp, color = c.textTertiary)
            }
        }
        Text(
            highlighted,
            style = CodeStyle.copy(color = c.codeText),
            softWrap = false,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        )
    }
}

private fun highlight(code: String, lang: String, c: ClaudeColors): AnnotatedString = buildAnnotatedString {
    append(code)
    for (t in CodeHighlighter.highlight(code, lang)) {
        val color = when (t.kind) {
            TokenKind.KEYWORD, TokenKind.TAG -> c.synKeyword
            TokenKind.STRING -> c.synString
            TokenKind.COMMENT -> c.synComment
            TokenKind.NUMBER -> c.synNumber
            TokenKind.TYPE, TokenKind.ATTR, TokenKind.ANNOTATION -> c.synType
            TokenKind.FUNCTION -> c.synFunction
        }
        val style = if (t.kind == TokenKind.COMMENT) SpanStyle(color = color, fontStyle = FontStyle.Italic) else SpanStyle(color = color)
        addStyle(style, t.start, t.end)
    }
}

@Composable
private fun rememberInline(inlines: List<Inline>): AnnotatedString {
    val c = LocalClaude.current
    return remember(inlines, c) {
        val code = SpanStyle(fontFamily = FontFamily.Monospace, background = c.codeBg, fontSize = 14.sp, color = c.codeText)
        val link = TextLinkStyles(SpanStyle(color = c.accent, textDecoration = TextDecoration.Underline))
        buildAnnotatedString { appendInlines(inlines, code, link) }
    }
}

private fun AnnotatedString.Builder.appendInlines(inlines: List<Inline>, code: SpanStyle, link: TextLinkStyles) {
    for (n in inlines) when (n) {
        is Inline.Text -> append(n.text)
        is Inline.Code -> withStyle(code) { append(n.code) }
        is Inline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { appendInlines(n.children, code, link) }
        is Inline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInlines(n.children, code, link) }
        is Inline.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { appendInlines(n.children, code, link) }
        is Inline.Link -> withLink(LinkAnnotation.Url(n.url, link)) { appendInlines(n.children, code, link) }
        Inline.LineBreak -> append('\n')
    }
}
