package com.artie.heart.sims.release

import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import kotlin.test.assertEquals

/** dev-environment.feature: Dev, promoted to over HTTP as a HEART client does, on the ETag it read. */
class DevSteps {
    private val mapper = ObjectMapper()
    private lateinit var dev: DevEnvironment
    private var answer: Response? = null
    private var readBefore: String? = null
    private var run: String? = null

    @Given("Dev starts out serving commit {string}")
    fun serves(commit: String) {
        dev = DevEnvironment(commit)
    }

    @When(
        "^the commit \"(\\w+)\" (?:is|was) promoted to Dev( on the version read before)?, " +
            "citing a smoke run where (\\d+) of (\\d+) scenarios passed$",
    )
    fun promotes(commit: String, before: String?, passed: Int, of: Int) {
        val etag = if (before != null) checkNotNull(readBefore) else read()
        if (before == null) readBefore = etag
        run = "http://localhost:8160/smoke-runs/$commit"
        val body = mapOf(
            "commit" to commit,
            "smoke" to mapOf("passed" to passed, "of" to of),
            "_links" to mapOf("env:smoke-run" to mapOf("href" to run)),
        )
        answer = dev.handler(
            get("/environments/1/actions/promote").method(Method.POST).header("If-Match", etag)
                .header("Content-Type", "application/json").body(mapper.writeValueAsString(body)),
        )
    }

    @Then("Dev answers {int}")
    fun answers(status: Int) = assertEquals(status, answer?.status?.code, answer?.bodyString())

    @Then("Dev answers {int}, a refusal of type {string}")
    fun refused(status: Int, type: String) {
        answers(status)
        val said = mapper.readTree(answer?.bodyString()).path("type").asText()
        assertEquals("https://w3id.org/heart-rise/problems/$type", said)
    }

    @Then("Dev serves commit {string}")
    fun servesNow(commit: String) = assertEquals(commit, dev.dev().commit)

    @Then("Dev serves commit {string}, citing that smoke run")
    fun servesCiting(commit: String) {
        servesNow(commit)
        assertEquals("http://localhost:8160/smoke-runs/$commit", dev.dev().smokeRun)
    }

    private fun read(): String = checkNotNull(dev.handler(get("/environments/1")).header("ETag"))

    private fun get(path: String) = Request(Method.GET, path).header("Authorization", "Bearer ${DevEnvironment.TOKEN}")
        .header("Accept", "application/hal+json")
}
