package com.artie.tickets.config

import com.artie.heart.Acted
import com.artie.heart.Found
import com.artie.heart.HeartResource
import com.artie.heart.Past
import com.artie.heart.PastEvent
import com.artie.tickets.declaration.TicketActs
import com.artie.tickets.declaration.Tickets
import com.artie.tickets.domain.Status
import com.artie.tickets.domain.TICKETS
import com.artie.tickets.domain.TicketService
import com.artie.tickets.domain.TicketState
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Declares the ticket to the HEART library: the declaration, how a ticket is
 * read, what each act records (the bookmark's included), how tickets are
 * found, and a ticket's past. The library serves the rest over HTTP and CoAP.
 */
@Configuration
class HeartConfig {
    @Bean
    fun tickets(service: TicketService): HeartResource<TicketState, Status> {
        val acts = TicketActs(service)
        return HeartResource(
            layers = Tickets.layers,
            load = { id -> service.state(id) },
            perform = { id, act, input -> Acted(acts.perform(id, act, input)) },
            find = { subject, id -> service.find(subject, id).map { Found(it.id, it.subject) } },
            past = Past(
                at = { id, instant -> service.state(id, instant) },
                history = { id ->
                    service.history(id).map {
                        PastEvent(it.sequenceNo, TICKETS.typeOf(it.event), it.occurredAt, it.event)
                    }
                },
            ),
            performAtBookmark = { _, input -> Acted(acts.open(input)) },
        )
    }
}
