package io.github.eelnicoo.pocketcode.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/** Resolves tool paths against a base working directory, allowing absolute paths too. */
class Workspace(val baseDir: () -> File) {
    fun resolve(path: String): File {
        val f = File(path)
        return if (f.isAbsolute) f else File(baseDir(), path)
    }
}

/** A single property in a JSON Schema object. */
data class Prop(val name: String, val type: String, val description: String, val required: Boolean = true)

/** Builds a JSON Schema "object" with string/typed properties from [props]. */
fun objectSchema(vararg props: Prop): JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {
        for (p in props) {
            put(p.name, buildJsonObject {
                put("type", p.type)
                put("description", p.description)
            })
        }
    })
    put("required", buildJsonArray {
        props.filter { it.required }.forEach { add(JsonPrimitive(it.name)) }
    })
}

class ReadFileTool(private val ws: Workspace) : Tool {
    override val name = "read_file"
    override val description = "Lee el contenido de un archivo de texto. Devuelve el texto con números de línea."
    override val inputSchema = objectSchema(
        Prop("path", "string", "Ruta del archivo (absoluta o relativa al directorio de trabajo)"),
    )
    override suspend fun run(input: JsonObject): ToolResult {
        val path = input["path"]?.jsonPrimitive?.content ?: return ToolResult("Falta 'path'", true)
        val f = ws.resolve(path)
        if (!f.exists()) return ToolResult("No existe: ${f.path}", true)
        if (f.isDirectory) return ToolResult("Es un directorio: ${f.path}", true)
        if (f.length() > 2_000_000) return ToolResult("Archivo demasiado grande (${f.length()} bytes)", true)
        val numbered = f.readText().lineSequence().withIndex()
            .joinToString("\n") { (i, line) -> "${(i + 1).toString().padStart(5)}\t$line" }
        return ToolResult(numbered)
    }
}

class WriteFileTool(private val ws: Workspace) : Tool {
    override val name = "write_file"
    override val description = "Crea o sobrescribe un archivo con el contenido dado."
    override val inputSchema = objectSchema(
        Prop("path", "string", "Ruta del archivo"),
        Prop("content", "string", "Contenido completo del archivo"),
    )
    override suspend fun run(input: JsonObject): ToolResult {
        val path = input["path"]?.jsonPrimitive?.content ?: return ToolResult("Falta 'path'", true)
        val content = input["content"]?.jsonPrimitive?.content ?: ""
        val f = ws.resolve(path)
        f.parentFile?.mkdirs()
        f.writeText(content)
        return ToolResult("Escrito ${content.length} caracteres en ${f.path}")
    }
}

class EditFileTool(private val ws: Workspace) : Tool {
    override val name = "edit_file"
    override val description = "Reemplaza exactamente una ocurrencia de 'old' por 'new' en un archivo."
    override val inputSchema = objectSchema(
        Prop("path", "string", "Ruta del archivo"),
        Prop("old", "string", "Texto exacto a reemplazar (debe ser único)"),
        Prop("new", "string", "Texto nuevo"),
    )
    override suspend fun run(input: JsonObject): ToolResult {
        val path = input["path"]?.jsonPrimitive?.content ?: return ToolResult("Falta 'path'", true)
        val old = input["old"]?.jsonPrimitive?.content ?: return ToolResult("Falta 'old'", true)
        val new = input["new"]?.jsonPrimitive?.content ?: ""
        val f = ws.resolve(path)
        if (!f.exists()) return ToolResult("No existe: ${f.path}", true)
        val text = f.readText()
        val count = text.split(old).size - 1
        if (count == 0) return ToolResult("No se encontró 'old' en el archivo", true)
        if (count > 1) return ToolResult("'old' aparece $count veces; debe ser único", true)
        f.writeText(text.replaceFirst(old, new))
        return ToolResult("Editado ${f.path}")
    }
}

class ListDirTool(private val ws: Workspace) : Tool {
    override val name = "list_dir"
    override val description = "Lista archivos y subdirectorios de un directorio."
    override val inputSchema = objectSchema(
        Prop("path", "string", "Ruta del directorio (usa '.' para el directorio de trabajo)"),
    )
    override suspend fun run(input: JsonObject): ToolResult {
        val path = input["path"]?.jsonPrimitive?.content ?: "."
        val f = ws.resolve(path)
        if (!f.exists()) return ToolResult("No existe: ${f.path}", true)
        if (!f.isDirectory) return ToolResult("No es un directorio: ${f.path}", true)
        val entries = f.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
        if (entries.isEmpty()) return ToolResult("(vacío) ${f.path}")
        val out = entries.joinToString("\n") {
            if (it.isDirectory) "${it.name}/" else "${it.name}  (${it.length()} B)"
        }
        return ToolResult("${f.path}:\n$out")
    }
}

class GrepTool(private val ws: Workspace) : Tool {
    override val name = "grep"
    override val description = "Busca un patrón (regex) en archivos de un directorio, recursivamente."
    override val inputSchema = objectSchema(
        Prop("pattern", "string", "Expresión regular a buscar"),
        Prop("path", "string", "Directorio raíz (por defecto, el de trabajo)", required = false),
    )
    override suspend fun run(input: JsonObject): ToolResult {
        val pattern = input["pattern"]?.jsonPrimitive?.content ?: return ToolResult("Falta 'pattern'", true)
        val path = input["path"]?.jsonPrimitive?.content ?: "."
        val root = ws.resolve(path)
        val regex = runCatching { Regex(pattern) }.getOrElse { return ToolResult("Regex inválida: ${it.message}", true) }
        val hits = StringBuilder()
        var count = 0
        root.walkTopDown()
            .filter { it.isFile && it.length() < 2_000_000 }
            .forEach { file ->
                runCatching {
                    file.useLines { lines ->
                        lines.forEachIndexed { i, line ->
                            if (count < 200 && regex.containsMatchIn(line)) {
                                hits.append("${file.path}:${i + 1}: ${line.trim().take(200)}\n")
                                count++
                            }
                        }
                    }
                }
            }
        return if (count == 0) ToolResult("Sin coincidencias") else ToolResult(hits.toString())
    }
}
