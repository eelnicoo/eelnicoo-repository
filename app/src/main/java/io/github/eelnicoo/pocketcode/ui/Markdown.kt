package io.github.eelnicoo.pocketcode.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface Block {
    data class Code(val lang: String, val text: String) : Block
    data class Lines(val lines: List<String>) : Block
}

private fun parse(markdown: String): List<Block> {
    val blocks = ArrayList<Block>()
    val lines = markdown.split("\n")
    var i = 0
    val buffer = ArrayList<String>()
    fun flush() {
        if (buffer.isNotEmpty()) { blocks.add(Block.Lines(buffer.toList())); buffer.clear() }
    }
    while (i < lines.size) {
        val line = lines[i]
        if (line.trimStart().startsWith("```")) {
            flush()
            val lang = line.trimStart().removePrefix("```").trim()
            val code = ArrayList<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                code.add(lines[i]); i++
            }
            blocks.add(Block.Code(lang, code.joinToString("\n")))
            i++ // skip closing fence
        } else {
            buffer.add(line); i++
        }
    }
    flush()
    return blocks
}

@Composable
fun MarkdownText(markdown: String, color: Color, modifier: Modifier = Modifier) {
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (block in parse(markdown)) {
            when (block) {
                is Block.Code -> CodeBlock(block.lang, block.text)
                is Block.Lines -> for (line in block.lines) MdLine(line, color, codeBg)
            }
        }
    }
}

@Composable
private fun MdLine(line: String, color: Color, codeBg: Color) {
    val trimmed = line.trimStart()
    when {
        trimmed.isEmpty() -> Text(" ", fontSize = 6.sp)
        trimmed.startsWith("### ") -> Heading(trimmed.removePrefix("### "), 15.sp, color, codeBg)
        trimmed.startsWith("## ") -> Heading(trimmed.removePrefix("## "), 17.sp, color, codeBg)
        trimmed.startsWith("# ") -> Heading(trimmed.removePrefix("# "), 19.sp, color, codeBg)
        trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
            Row {
                Text("•  ", color = MaterialTheme.colorScheme.primary)
                Text(inline(trimmed.drop(2), codeBg), color = color)
            }
        Regex("^\\d+\\. ").containsMatchIn(trimmed) -> {
            val num = trimmed.substringBefore(". ")
            Row {
                Text("$num.  ", color = MaterialTheme.colorScheme.primary)
                Text(inline(trimmed.substringAfter(". "), codeBg), color = color)
            }
        }
        else -> Text(inline(line, codeBg), color = color)
    }
}

@Composable
private fun Heading(text: String, size: androidx.compose.ui.unit.TextUnit, color: Color, codeBg: Color) {
    Text(
        inline(text, codeBg),
        color = color,
        fontSize = size,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

/** Inline markdown: `code`, **bold**, *italic*. */
private fun inline(text: String, codeBg: Color): AnnotatedString = buildAnnotatedString {
    val segments = text.split("`")
    segments.forEachIndexed { idx, seg ->
        if (idx % 2 == 1) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, fontSize = 13.sp)) {
                append(seg)
            }
        } else {
            appendEmphasis(seg)
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendEmphasis(text: String) {
    val regex = Regex("\\*\\*(.+?)\\*\\*|\\*(.+?)\\*|__(.+?)__")
    var last = 0
    for (m in regex.findAll(text)) {
        if (m.range.first > last) append(text.substring(last, m.range.first))
        val bold = m.groupValues[1].ifEmpty { m.groupValues[3] }
        val italic = m.groupValues[2]
        if (bold.isNotEmpty()) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
        else if (italic.isNotEmpty()) withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
        last = m.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}

@Composable
private fun CodeBlock(lang: String, code: String) {
    val clipboard = LocalClipboardManager.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    lang.ifBlank { "código" },
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copiar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(2.dp)
                        .clickable { clipboard.setText(AnnotatedString(code)) },
                )
            }
            Text(
                code,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 6.dp),
                style = CodeStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
