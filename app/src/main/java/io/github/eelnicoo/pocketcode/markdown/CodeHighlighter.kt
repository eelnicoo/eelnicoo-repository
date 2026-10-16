package io.github.eelnicoo.pocketcode.markdown

enum class TokenKind { KEYWORD, STRING, COMMENT, NUMBER, TYPE, FUNCTION, ANNOTATION, TAG, ATTR }

data class Token(val start: Int, val end: Int, val kind: TokenKind)

/**
 * Lightweight regex-based syntax highlighter. It is not a full lexer; it only aims to
 * make common languages in chat answers easier to read. Returns non-overlapping tokens.
 */
object CodeHighlighter {

    private val KEYWORDS: Map<String, Set<String>> = run {
        val kotlin = setOf(
            "fun", "val", "var", "class", "object", "interface", "if", "else", "when", "for", "while", "do",
            "return", "import", "package", "private", "public", "internal", "protected", "override", "open",
            "abstract", "data", "sealed", "enum", "companion", "suspend", "inline", "null", "true", "false",
            "this", "super", "is", "as", "in", "try", "catch", "finally", "throw", "typealias", "lateinit",
            "const", "by", "init", "constructor", "break", "continue", "reified", "operator", "infix",
        )
        val java = setOf(
            "public", "private", "protected", "class", "interface", "enum", "extends", "implements", "static",
            "final", "void", "int", "long", "double", "float", "boolean", "char", "byte", "short", "new",
            "return", "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue",
            "try", "catch", "finally", "throw", "throws", "import", "package", "null", "true", "false", "this",
            "super", "abstract", "synchronized", "volatile", "instanceof", "var", "record",
        )
        val js = setOf(
            "function", "const", "let", "var", "if", "else", "for", "while", "do", "return", "import", "export",
            "from", "default", "class", "extends", "new", "this", "super", "null", "undefined", "true", "false",
            "async", "await", "try", "catch", "finally", "throw", "typeof", "instanceof", "in", "of", "switch",
            "case", "break", "continue", "interface", "type", "enum", "implements", "public", "private",
            "readonly", "static", "yield", "void", "as",
        )
        val py = setOf(
            "def", "class", "if", "elif", "else", "for", "while", "return", "import", "from", "as", "try",
            "except", "finally", "raise", "with", "yield", "lambda", "pass", "break", "continue", "None",
            "True", "False", "and", "or", "not", "in", "is", "global", "nonlocal", "async", "await", "self",
        )
        val sh = setOf(
            "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac", "in", "function",
            "return", "export", "local", "echo", "cd", "set", "unset", "source", "exit", "sudo", "adb", "pm",
            "am", "settings", "cmd", "getprop", "setprop",
        )
        val c = setOf(
            "int", "long", "char", "float", "double", "void", "struct", "union", "enum", "typedef", "const",
            "static", "extern", "if", "else", "for", "while", "do", "return", "switch", "case", "break",
            "continue", "sizeof", "include", "define", "class", "public", "private", "protected", "template",
            "namespace", "using", "new", "delete", "auto", "nullptr", "true", "false", "virtual", "override",
        )
        val go = setOf(
            "func", "package", "import", "var", "const", "type", "struct", "interface", "map", "chan", "go",
            "defer", "if", "else", "for", "range", "return", "switch", "case", "default", "break", "continue",
            "nil", "true", "false", "select",
        )
        val rust = setOf(
            "fn", "let", "mut", "const", "struct", "enum", "impl", "trait", "pub", "use", "mod", "crate", "self",
            "Self", "if", "else", "match", "for", "while", "loop", "return", "true", "false", "as", "ref",
            "move", "async", "await", "where", "dyn", "unsafe",
        )
        val sql = setOf(
            "select", "from", "where", "insert", "into", "values", "update", "set", "delete", "create", "table",
            "drop", "alter", "join", "left", "right", "inner", "outer", "on", "group", "by", "order", "having",
            "limit", "and", "or", "not", "null", "as", "distinct", "primary", "key", "index",
        )
        mapOf(
            "kotlin" to kotlin, "kt" to kotlin, "kts" to kotlin, "gradle" to kotlin,
            "java" to java, "js" to js, "javascript" to js, "ts" to js, "typescript" to js, "jsx" to js,
            "tsx" to js, "python" to py, "py" to py, "bash" to sh, "sh" to sh, "shell" to sh, "zsh" to sh,
            "console" to sh, "c" to c, "cpp" to c, "c++" to c, "h" to c, "go" to go, "rust" to rust,
            "rs" to rust, "sql" to sql, "swift" to kotlin, "dart" to java, "csharp" to java, "cs" to java,
        )
    }

    private val hashComment = setOf("python", "py", "bash", "sh", "shell", "zsh", "console", "yaml", "yml", "toml", "ruby", "rb")

    private val stringRe = Regex("\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'|`(?:\\\\.|[^`\\\\])*`")
    private val numberRe = Regex("\\b(0x[0-9a-fA-F_]+|\\d[\\d_]*(\\.\\d+)?([eE][+-]?\\d+)?[fFLlu]?)\\b")
    private val wordRe = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val annotationRe = Regex("@[A-Za-z_][A-Za-z0-9_.]*")
    private val tagRe = Regex("</?[A-Za-z][A-Za-z0-9:_.-]*|/?>")
    private val attrRe = Regex("\\b[A-Za-z_:][A-Za-z0-9_:.-]*(?==)")
    private val jsonKeyRe = Regex("\"(?:\\\\.|[^\"\\\\])*\"(?=\\s*:)")

    fun highlight(code: String, lang: String): List<Token> {
        val l = lang.lowercase().trim()
        val tokens = ArrayList<Token>()
        val taken = BooleanArray(code.length)

        fun add(start: Int, end: Int, kind: TokenKind) {
            if (start >= end || start < 0 || end > code.length) return
            for (k in start until end) if (taken[k]) return
            for (k in start until end) taken[k] = true
            tokens.add(Token(start, end, kind))
        }

        when (l) {
            "xml", "html", "svg", "manifest" -> {
                Regex("<!--[\\s\\S]*?-->").findAll(code).forEach { add(it.range.first, it.range.last + 1, TokenKind.COMMENT) }
                stringRe.findAll(code).forEach { add(it.range.first, it.range.last + 1, TokenKind.STRING) }
                tagRe.findAll(code).forEach { add(it.range.first, it.range.last + 1, TokenKind.TAG) }
                attrRe.findAll(code).forEach { add(it.range.first, it.range.last + 1, TokenKind.ATTR) }
                return tokens.sortedBy { it.start }
            }
            "json", "jsonc" -> {
                jsonKeyRe.findAll(code).forEach { add(it.range.first, it.range.last + 1, TokenKind.ATTR) }
                stringRe.findAll(code).forEach { add(it.range.first, it.range.last + 1, TokenKind.STRING) }
                numberRe.findAll(code).forEach { add(it.range.first, it.range.last + 1, TokenKind.NUMBER) }
                Regex("\\b(true|false|null)\\b").findAll(code).forEach { add(it.range.first, it.range.last + 1, TokenKind.KEYWORD) }
                return tokens.sortedBy { it.start }
            }
        }

        // Comments first so strings/keywords inside them are not highlighted.
        val commentRe = if (l in hashComment) Regex("#[^\\n]*") else Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/")
        // Strings take priority over comment markers inside them, so resolve by position.
        val events = (commentRe.findAll(code).map { it to TokenKind.COMMENT } + stringRe.findAll(code).map { it to TokenKind.STRING })
            .sortedBy { it.first.range.first }
        for ((m, kind) in events) add(m.range.first, m.range.last + 1, kind)

        if (l !in hashComment) annotationRe.findAll(code).forEach { add(it.range.first, it.range.last + 1, TokenKind.ANNOTATION) }
        numberRe.findAll(code).forEach { add(it.range.first, it.range.last + 1, TokenKind.NUMBER) }

        val keywords = KEYWORDS[l] ?: (KEYWORDS["kotlin"].orEmpty() + KEYWORDS["python"].orEmpty() + KEYWORDS["js"].orEmpty())
        val caseInsensitive = l == "sql"
        wordRe.findAll(code).forEach { m ->
            val w = m.value
            val isKw = if (caseInsensitive) w.lowercase() in keywords else w in keywords
            when {
                isKw -> add(m.range.first, m.range.last + 1, TokenKind.KEYWORD)
                m.range.last + 1 < code.length && code[m.range.last + 1] == '(' -> add(m.range.first, m.range.last + 1, TokenKind.FUNCTION)
                w[0].isUpperCase() && w.length > 1 && l != "bash" && l != "sh" -> add(m.range.first, m.range.last + 1, TokenKind.TYPE)
            }
        }
        return tokens.sortedBy { it.start }
    }
}
