package com.artie.heart.gateway.bdd

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import java.nio.file.Files
import java.nio.file.Path

/** A connector that renames an operation's field, and the form that asks for it (github-issue-comments.feature). */
class RenameSteps(private val github: GitHubSteps) {

    /**
     * The shipped GitHub connector, copied, with the act [act] asking for [now] where the operation takes [was]:
     * its `fields` name the new name, and `rename` maps it to the operation's own.
     */
    @Given(RENAMING)
    fun renaming(act: String, was: String, now: String) {
        val copy = Files.createTempDirectory("github-connector")
        Files.list(Path.of("connectors", "github")).use { files ->
            files.forEach { Files.copy(it, copy.resolve(it.fileName)) }
        }
        val declared = Files.list(copy).use { files -> files.filter { "  $act:" in Files.readAllLines(it) }.toList() }
        check(declared.isNotEmpty()) { "no connector file declares the act $act" }
        declared.forEach { file ->
            val lines = Files.readAllLines(file)
            val start = lines.indexOf("  $act:")
            val fields = (start + 1 until lines.size).first { lines[it].trimStart().startsWith("fields:") }
            lines[fields] = lines[fields].replace(Regex("\\b${Regex.escape(was)}\\b"), now)
            lines.add(fields + 1, "    rename: { $now: $was }")
            Files.write(file, lines)
        }
        github.readsFrom(copy)
    }

    /** Every field the form asks for, in order: whether it is required, and how it is described. */
    @Then("the {string} form asks for these fields")
    fun asksFor(key: String, table: DataTable) {
        val schema = github.lastAnswer.path("_forms").path(key).path("schema")
        val required = schema.path("required").map { it.asText() }.toSet()
        val asked = schema.path("properties").properties().map { (field, property) ->
            mapOf(
                "field" to field,
                "required" to if (field in required) "yes" else "no",
                "description" to property.path("description").asText(),
            )
        }
        assertEquals(table.asMaps(), asked, "$schema")
    }

    private companion object {
        const val RENAMING = "the GitHub gateway reads GitHub's description with the shipped connector, " +
            "its act {string} renaming {string} to {string}"
    }
}
