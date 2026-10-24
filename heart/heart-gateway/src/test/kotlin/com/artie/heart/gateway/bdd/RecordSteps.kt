package com.artie.heart.gateway.bdd

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Files

/** upstream-record.feature: the gateway's record of its upstream calls, read as YAML. */
class RecordSteps(private val github: GitHubSteps) {

    @Given("the GitHub gateway keeps a record of its upstream calls")
    fun keeps() {
        github.recordAt(Files.createTempFile("upstream-record", ".yaml").also { Files.delete(it) })
    }

    @When("{string} gets {string} through the GitHub gateway, with the traceparent {string}")
    fun getWithTrace(caller: String, path: String, traceparent: String) = github.getAs(caller, path, traceparent)

    @Then("the record ties these upstream calls, among others, to the traceparent {string}")
    fun ties(traceparent: String, table: DataTable) {
        val tied = documents().filter { it.path("traceparent").asText() == traceparent }.map { call ->
            mapOf(
                "method" to call.path("request").path("method").asText(),
                "path" to java.net.URI(call.path("request").path("url").asText()).path,
                "status" to call.path("response").path("status").asText(),
            )
        }
        table.asMaps().forEach { row -> assertTrue(row in tied, "$row not among $tied") }
    }

    @Then("the record is YAML, one document per upstream call, each credential written {string}")
    fun isYaml(token: String) {
        val calls = documents()
        assertTrue(calls.isNotEmpty(), "no upstream call in the record")
        // For a person to read: no value broken across lines with backslash escapes.
        val text = Files.readString(checkNotNull(github.record))
        assertTrue(!Regex("\\\\\\n").containsMatchIn(text), "a value split with backslashes")
        calls.forEach { call ->
            assertEquals(token, call.path("request").path("headers").path("Authorization").asText(), "$call")
        }
    }

    private fun documents(): List<JsonNode> {
        val file = checkNotNull(github.record) { "no record asked for" }
        check(Files.exists(file)) { "no record at $file" }
        val parser = YAMLFactory().createParser(file.toFile())
        return ObjectMapper(YAMLFactory()).readValues(parser, JsonNode::class.java).readAll()
    }
}
