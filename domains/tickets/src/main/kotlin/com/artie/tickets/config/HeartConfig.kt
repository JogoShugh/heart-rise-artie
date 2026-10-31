package com.artie.tickets.config

import com.artie.heart.Acted
import com.artie.heart.Found
import com.artie.heart.HeartResource
import com.artie.heart.Listing
import com.artie.tickets.domain.Agents
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
    /** The desk's agents at /agents{?priority}, where the assign act's choices come from (Tickets). */
    @Bean
    fun agents() = Listing("/agents", "agents") { query -> Agents.handling(query["priority"]).map(Agents::json) }

    @Bean
    fun tickets(service: TicketService): HeartResource<TicketState, Status> {
        val acts = TicketActs(service)
        return HeartResource(
            layers = Tickets.layers,
            load = { id -> service.state(id) },
            perform = { ticket, act, input -> Acted(acts.perform(ticket, act, input)) },
            find = { subject, id -> service.find(subject, id).map { Found(it.id, it.subject) } },
            past = TICKETS.past(at = { id, instant -> service.state(id, instant) }, history = service::history),
            performAtBookmark = { _, input -> Acted(acts.open(input)) },
        )
    }
}
