package com.artie.heart.gateway.bdd

import io.cucumber.java.en.When

/** github-volatile.feature: what GitHub works out by itself, between the gateway's reads. */
class VolatileSteps(private val github: GitHubSteps) {
    @When("after the gateway's next read of pull request {long}, GitHub works out that it can be merged cleanly")
    fun mergeable(number: Long) {
        github.upstream.afterNextRead[number] = { it.mergeable = true; it.mergeableState = "clean" }
    }

    @When("after the gateway's next read of pull request {long}, GitHub works out that it is in conflict")
    fun conflict(number: Long) {
        github.upstream.afterNextRead[number] = { it.mergeable = false; it.mergeableState = "dirty" }
    }

    @When("after the gateway's next read of pull request {long}, its title changes upstream to {string}")
    fun retitled(number: Long, title: String) {
        github.upstream.afterNextRead[number] = { it.title = title }
    }
}
