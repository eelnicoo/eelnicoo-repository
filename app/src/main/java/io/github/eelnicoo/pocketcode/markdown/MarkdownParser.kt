package io.github.eelnicoo.pocketcode.markdown

/** Inline Markdown nodes. */
sealed interface Inline {
    data class Text(val text: String) : Inline
    data class Code(val code: String) : Inline
    data class Bold(val children: List<Inline>) : Inline
    data class Italic(val children: List<Inline>) : Inline
    data class Strike(val children: List<Inline>) : Inline
    data class Link(val children: List<Inline>, val url: String) : Inline
    data object LineBreak : Inline
}

/** Block Markdown nodes. */
sealed interface MdBlock {
    data class Paragraph(val inlines: List<Inline>) : MdBlock
    data class Heading(val level: Int, val inlines: List<Inline>) : MdBlock
    data class CodeBlock(val lang: String, val code: String, val closed: Boolean) : MdBlock
    data class Quote(val blocks: List<MdBlock>) : MdBlock
    data class ListItem(
        val level: Int,
        val ordered: Boolean,
        val number: Int,
        val task: Boolean?,
        val inlines: List<Inline>,
    ) : MdBlock
    data class Table(val header: List<List<Inline>>, val aligns: List<Align>, val rows: List<List<List<Inline>>>) : MdBlock
    data object Rule : MdBlock
}

enum class Align { LEFT, CENTER, RIGHT }

/**
 * A small, forgiving Markdown parser tuned for chat answers (CommonMark-ish subset):
 * fenced code (also while still streaming), headings, quotes, nested lists with task
 * boxes, GFM tables, rules, and inline code / bold / italic / strike / links / autolinks.
 * Single newlines inside a paragraph are kept as line breaks.
 */
object MarkdownParser {

    private val fenceRe = Regex("^ {0,3}(`{3,}|~{3,})\\s*([^`\\s]*)?.*$")
    private val headingRe = Regex("^ {0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$")
    private val ruleRe = Regex("^ {0,3}((\\*\\s*){3,}|(-\\s*){3,}|(_\\s*){3,})$")
    private val listRe = Regex("^(\\s*)([-*+]|(\\d{1,9})[.)])\\s+(.*)$")
    private val tableSepRe = Regex("^\\s*\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?\\s*$")

    fun parse(markdown: String): List<MdBlock> = parseLines(markdown.replace("\r\n", "\n").split("\n"))

    private fun parseLines(lines: List<String>): List<MdBlock> {
        val out = ArrayList<MdBlock>()
        val para = ArrayList<String>()
        var i = 0

        fun flushPara() {
            if (para.isNotEmpty()) {
                out.add(MdBlock.Paragraph(parseInline(para.joinToString("\n"))))
                para.clear()
            }
        }

        while (i < lines.size) {
            val line = lines[i]
            val fence = fenceRe.matchEntire(line)
            when {
                fence != null -> {
                    flushPara()
                    val marker = fence.groupValues[1]
                    val lang = fence.groupValues.getOrNull(2).orEmpty()
                    val code = ArrayList<String>()
                    var closed = false
                    i++
                    while (i < lines.size) {
                        val t = lines[i].trim()
                        if (t.length >= marker.length && t.all { it == marker[0] }) { closed = true; i++; break }
                        code.add(lines[i]); i++
                    }
                    out.add(MdBlock.CodeBlock(lang, code.joinToString("\n"), closed))
                    continue
                }
                line.isBlank() -> flushPara()
                headingRe.matches(line) -> {
                    flushPara()
                    val m = headingRe.matchEntire(line)!!
                    out.add(MdBlock.Heading(m.groupValues[1].length, parseInline(m.groupValues[2])))
                }
                ruleRe.matches(line) -> { flushPara(); out.add(MdBlock.Rule) }
                line.trimStart().startsWith(">") -> {
                    flushPara()
                    val quoted = ArrayList<String>()
                    while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                        quoted.add(lines[i].trimStart().removePrefix(">").removePrefix(" "))
                        i++
                    }
                    out.add(MdBlock.Quote(parseLines(quoted)))
                    continue
                }
                isTableStart(lines, i) -> {
                    flushPara()
                    val header = splitRow(lines[i])
                    val aligns = splitRow(lines[i + 1]).map { cell ->
                        val c = cell.trim()
                        when {
                            c.startsWith(":") && c.endsWith(":") -> Align.CENTER
                            c.endsWith(":") -> Align.RIGHT
                            else -> Align.LEFT
                        }
                    }
                    i += 2
                    val rows = ArrayList<List<List<Inline>>>()
                    while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                        val cells = splitRow(lines[i])
                        rows.add(List(header.size) { k -> parseInline(cells.getOrElse(k) { "" }) })
                        i++
                    }
                    out.add(MdBlock.Table(header.map { parseInline(it) }, List(header.size) { aligns.getOrElse(it) { Align.LEFT } }, rows))
                    continue
                }
                listRe.matches(line) -> {
                    flushPara()
                    val m = listRe.matchEntire(line)!!
                    val indent = m.groupValues[1].replace("\t", "    ").length
                    val num = m.groupValues[3]
                    var text = m.groupValues[4]
                    var task: Boolean? = null
                    if (text.startsWith("[ ] ")) { task = false; text = text.drop(4) }
                    else if (text.startsWith("[x] ") || text.startsWith("[X] ")) { task = true; text = text.drop(4) }
                    // Lazy continuation lines belong to this item.
                    val buf = StringBuilder(text)
                    i++
                    while (i < lines.size && lines[i].isNotBlank() && !listRe.matches(lines[i]) &&
                        fenceRe.matchEntire(lines[i]) == null && !headingRe.matches(lines[i]) &&
                        lines[i].startsWith(" ")
                    ) {
                        buf.append('\n').append(lines[i].trim()); i++
                    }
                    out.add(MdBlock.ListItem(indent / 2, num.isNotEmpty(), num.toIntOrNull() ?: 0, task, parseInline(buf.toString())))
                    continue
                }
                else -> para.add(line)
            }
            i++
        }
        flushPara()
        return out
    }

    private fun isTableStart(lines: List<String>, i: Int): Boolean =
        i + 1 < lines.size && lines[i].contains('|') && tableSepRe.matches(lines[i + 1]) && lines[i + 1].contains('-')

    private fun splitRow(line: String): List<String> {
        var s = line.trim()
        if (s.startsWith("|")) s = s.drop(1)
        if (s.endsWith("|") && !s.endsWith("\\|")) s = s.dropLast(1)
        val cells = ArrayList<String>()
        val cur = StringBuilder()
        var inCode = false
        var k = 0
        while (k < s.length) {
            val c = s[k]
            when {
                c == '\\' && k + 1 < s.length && s[k + 1] == '|' -> { cur.append('|'); k++ }
                c == '`' -> { inCode = !inCode; cur.append(c) }
                c == '|' && !inCode -> { cells.add(cur.toString().trim()); cur.clear() }
                else -> cur.append(c)
            }
            k++
        }
        cells.add(cur.toString().trim())
        return cells
    }

    // ---------------------------------------------------------------- inline

    private const val ESCAPABLE = "\\`*_{}[]()#+-.!|~<>"

    fun parseInline(s: String): List<Inline> {
        val out = ArrayList<Inline>()
        val buf = StringBuilder()
        fun flush() { if (buf.isNotEmpty()) { out.add(Inline.Text(buf.toString())); buf.clear() } }
        var i = 0
        val n = s.length
        while (i < n) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < n && s[i + 1] in ESCAPABLE -> { buf.append(s[i + 1]); i += 2 }
                c == '\n' -> { flush(); out.add(Inline.LineBreak); i++ }
                c == '`' -> {
                    val run = countRun(s, i, '`')
                    val close = findBacktickClose(s, i + run, run)
                    if (close < 0) { buf.append(s, i, i + run); i += run }
                    else {
                        flush()
                        var code = s.substring(i + run, close)
                        if (code.length >= 2 && code.startsWith(" ") && code.endsWith(" ") && code.isNotBlank()) {
                            code = code.substring(1, code.length - 1)
                        }
                        out.add(Inline.Code(code))
                        i = close + run
                    }
                }
                (s.startsWith("**", i) || s.startsWith("__", i)) && canOpen(s, i, 2) -> {
                    val delim = s.substring(i, i + 2)
                    val close = findClose(s, i + 2, delim)
                    if (close < 0) { buf.append(delim); i += 2 }
                    else { flush(); out.add(Inline.Bold(parseInline(s.substring(i + 2, close)))); i = close + 2 }
                }
                s.startsWith("~~", i) && canOpen(s, i, 2) -> {
                    val close = findClose(s, i + 2, "~~")
                    if (close < 0) { buf.append("~~"); i += 2 }
                    else { flush(); out.add(Inline.Strike(parseInline(s.substring(i + 2, close)))); i = close + 2 }
                }
                (c == '*' || c == '_') && canOpen(s, i, 1) && !(c == '_' && i > 0 && s[i - 1].isLetterOrDigit()) -> {
                    val close = findClose(s, i + 1, c.toString())
                    if (close < 0 || (c == '_' && close + 1 < n && s[close + 1].isLetterOrDigit())) { buf.append(c); i++ }
                    else { flush(); out.add(Inline.Italic(parseInline(s.substring(i + 1, close)))); i = close + 1 }
                }
                c == '[' -> {
                    val link = parseLink(s, i)
                    if (link == null) { buf.append(c); i++ }
                    else { flush(); out.add(Inline.Link(parseInline(link.first), link.second)); i = link.third }
                }
                (s.startsWith("https://", i) || s.startsWith("http://", i)) && (i == 0 || !s[i - 1].isLetterOrDigit()) -> {
                    var j = i
                    while (j < n && !s[j].isWhitespace() && s[j] != '<' && s[j] != '>') j++
                    // Trailing punctuation is not part of the URL.
                    while (j > i && s[j - 1] in ".,;:!?\"')*_") {
                        if (s[j - 1] == ')' && s.substring(i, j).count { it == '(' } >= s.substring(i, j).count { it == ')' }) break
                        j--
                    }
                    val url = s.substring(i, j)
                    flush()
                    out.add(Inline.Link(listOf(Inline.Text(url)), url))
                    i = j
                }
                else -> { buf.append(c); i++ }
            }
        }
        flush()
        return out
    }

    private fun countRun(s: String, i: Int, ch: Char): Int {
        var k = i
        while (k < s.length && s[k] == ch) k++
        return k - i
    }

    private fun findBacktickClose(s: String, from: Int, run: Int): Int {
        var k = from
        while (k < s.length) {
            if (s[k] == '`') {
                val r = countRun(s, k, '`')
                if (r == run) return k
                k += r
            } else k++
        }
        return -1
    }

    /** An opening delimiter must be followed by a non-space character. */
    private fun canOpen(s: String, i: Int, len: Int): Boolean =
        i + len < s.length && !s[i + len].isWhitespace()

    /**
     * Finds the closing [delim] after [from], skipping code spans, requiring a non-space
     * character before it and a non-empty body.
     */
    private fun findClose(s: String, from: Int, delim: String): Int {
        var k = from
        while (k < s.length) {
            val c = s[k]
            if (c == '\\') { k += 2; continue }
            if (c == '`') {
                val run = countRun(s, k, '`')
                val close = findBacktickClose(s, k + run, run)
                k = if (close < 0) k + run else close + run
                continue
            }
            if (s.startsWith(delim, k) && k > from && !s[k - 1].isWhitespace()) {
                if (delim.length == 1) {
                    val doubled = (k + 1 < s.length && s[k + 1] == delim[0]) || s[k - 1] == delim[0]
                    if (!doubled) return k
                    // Skip over a nested "**"/"__" pair as a unit.
                    if (k + 1 < s.length && s[k + 1] == delim[0]) {
                        val inner = findClose(s, k + 2, delim + delim)
                        k = if (inner < 0) k + 2 else inner + 2
                        continue
                    }
                } else return k
            }
            k++
        }
        return -1
    }

    /** Parses `[text](url)` at [i]; returns (text, url, endIndex) or null. */
    private fun parseLink(s: String, i: Int): Triple<String, String, Int>? {
        var depth = 0
        var k = i
        while (k < s.length) {
            when (s[k]) {
                '\\' -> k++
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) break }
                '\n' -> return null
            }
            k++
        }
        if (k >= s.length || k + 1 >= s.length || s[k + 1] != '(') return null
        val close = s.indexOf(')', k + 2)
        if (close < 0) return null
        val url = s.substring(k + 2, close).trim().substringBefore(' ').removeSurrounding("<", ">")
        if (url.isEmpty() || url.contains('\n')) return null
        return Triple(s.substring(i + 1, k), url, close + 1)
    }

    /** Plain text of inline nodes (for copy / accessibility). */
    fun plain(inlines: List<Inline>): String = buildString {
        for (n in inlines) when (n) {
            is Inline.Text -> append(n.text)
            is Inline.Code -> append(n.code)
            is Inline.Bold -> append(plain(n.children))
            is Inline.Italic -> append(plain(n.children))
            is Inline.Strike -> append(plain(n.children))
            is Inline.Link -> append(plain(n.children))
            Inline.LineBreak -> append('\n')
        }
    }
}
