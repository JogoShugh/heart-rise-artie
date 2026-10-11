package com.artie.heart.spring.bdd

import io.cucumber.spring.ScenarioScope
import org.springframework.stereotype.Component
import org.springframework.test.web.reactive.server.EntityExchangeResult

/** The responses of one scenario, shared by every step class. */
@Component
@ScenarioScope
class HttpWorld {
    var results: List<EntityExchangeResult<ByteArray>> = emptyList()
    val last: EntityExchangeResult<ByteArray> get() = results.last()
}
