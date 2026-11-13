package com.artie.heart.gateway.bdd

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import java.nio.file.Files
import java.nio.file.Path

/** A connector that says its upstream in a profile's own words, on the way out (normalized.feature). */
class NormalSteps(private val github: GitHubSteps) {

    /**
     * The shipped GitHub connector, copied, with the act [act] asking for [field] as one of the table's values, each
     * sent upstream as its members: `inputs` describes the field, `values` maps each value to what is sent.
     */
    @Given(TAKING)
    fun taking(act: String, field: String, table: DataTable) {
        val rows = table.asMaps()
        val copy = copied()
        val declared = Files.list(copy).use { files -> files.filter { "  $act:" in Files.readAllLines(it) }.toList() }
        check(declared.isNotEmpty()) { "no connector file declares the act $act" }
        declared.forEach { file ->
            val lines = Files.readAllLines(file)
            val start = lines.indexOf("  $act:")
            val values = rows.joinToString(", ") { it.getValue("value") }
            val added = listOf(
                "    inputs:",
                "      $field: { type: string, enum: [$values], title: Method, description: How to merge. }",
                "    values:",
                "      $field:",
            ) + rows.map { "        ${it.getValue("value")}: ${it.getValue("sends")}" }
            lines.addAll(start + 1, added)
            Files.write(file, lines)
        }
        github.readsFrom(copy)
    }

    /** The shipped GitHub connector, copied, with its pull requests' state saying the table's members as well. */
    @Given(SAYING)
    fun saying(table: DataTable) {
        val copy = copied()
        val file = copy.resolve("pull.resource.yaml")
        val lines = Files.readAllLines(file) + "normal:" + table.asMaps().flatMap { row ->
            val values = row["values"].orEmpty().ifBlank { null }
            listOfNotNull(
                "  ${row.getValue("member")}:",
                "    from: ${row.getValue("from")}",
                values?.let { "    values: $it" },
            )
        }
        Files.write(file, lines)
        github.readsFrom(copy)
    }

    /** The form [key] offers [field] as exactly these values, comma separated. */
    @Then("the {string} form offers {string} as one of {string}")
    fun offersOneOf(key: String, field: String, values: String) {
        val property = github.lastAnswer.path("_forms").path(key).path("schema").path("properties").path(field)
        assertEquals(values.split(',').map { it.trim() }, property.path("enum").map { it.asText() }, "$property")
    }

    /** The answer's state holds each value at its pointer. */
    @Then("the answer holds")
    fun holds(table: DataTable) {
        val body = github.lastAnswer
        table.asMaps().forEach { row ->
            assertEquals(row.getValue("value"), body.at(row.getValue("pointer")).asText(), "${row["pointer"]} in $body")
        }
    }

    private fun copied(): Path {
        val copy = Files.createTempDirectory("github-connector")
        Files.list(Path.of("connectors", "github")).use { files ->
            files.forEach { Files.copy(it, copy.resolve(it.fileName)) }
        }
        return copy
    }

    private companion object {
        const val TAKING = "the GitHub gateway reads GitHub's description with the shipped connector, " +
            "its act {string} taking {string} as"
        const val SAYING = "the GitHub gateway reads GitHub's description with the shipped connector, its pulls saying"
    }
}
