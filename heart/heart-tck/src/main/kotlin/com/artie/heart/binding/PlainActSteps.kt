package com.artie.heart.binding

import com.artie.heart.binding.HttpWorld.Companion.accept
import io.cucumber.java.en.When
import org.springframework.http.HttpHeaders

/**
 * A plain client that keeps HTTP's validator (spec/SPEC.md, "The OpenAPI twin keeps HTTP's validators"): it sends
 * back, as If-Match, the ETag its last read was answered with.
 */
class PlainActSteps(private val http: HttpWorld) {

    @When("^a client (posts|puts|patches|deletes) (.*) to \"(.+)\" as (\\S+) with the ETag it read$")
    fun actWithRead(verb: String, body: String, path: String, dialect: String) {
        val etag = checkNotNull(http.last.responseHeaders.eTag) { "the last read carried no ETag" }
        val headers = mapOf(HttpHeaders.ACCEPT to accept(dialect), HttpHeaders.IF_MATCH to etag)
        http.send(ActSteps.method(verb), path, body, headers)
    }
}
