package io.github.eelnicoo.pocketcode.tools

import io.github.eelnicoo.pocketcode.api.ToolDef
import kotlinx.serialization.json.JsonObject

/** Result of running a tool: text payload plus an error flag for the API tool_result. */
data class ToolResult(val text: String, val isError: Boolean = false)

interface Tool {
    val name: String
    val description: String
    /** JSON Schema object for the tool input. */
    val inputSchema: JsonObject
    suspend fun run(input: JsonObject): ToolResult

    fun toDef(): ToolDef = ToolDef(name, description, inputSchema)
}

class ToolRegistry(tools: List<Tool>) {
    private val byName = tools.associateBy { it.name }
    val all: List<Tool> = tools

    fun defs(): List<ToolDef> = all.map { it.toDef() }

    suspend fun execute(name: String, input: JsonObject): ToolResult {
        val tool = byName[name]
            ?: return ToolResult("Herramienta desconocida: $name", isError = true)
        return try {
            tool.run(input)
        } catch (t: Throwable) {
            ToolResult("Error en $name: ${t.message ?: t.toString()}", isError = true)
        }
    }
}
