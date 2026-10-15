package com.artie.tickets.declaration

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

// Each act's input. Its JSON Schema is generated from these annotations, and
// the body is checked against that same schema before the act runs.

private const val MAX_TEXT = 2000
private const val LOW = "low"
private const val NORMAL = "normal"
private const val HIGH = "high"
private const val URGENT = "urgent"
private const val PRIORITY = "How urgent the problem is"

@Schema(title = "Open a ticket")
data class OpenTicket(
    @field:NotNull @field:Size(min = 1, max = MAX_TEXT)
    @field:Schema(title = "Subject", description = "The problem in a few words", examples = ["Cannot log in"])
    val subject: String? = null,
    @field:NotNull @field:Size(min = 1, max = MAX_TEXT)
    @field:Schema(title = "Customer", description = "Who has the problem", examples = ["Dana Reyes"])
    val customer: String? = null,
    @field:NotNull @field:Size(min = 1, max = MAX_TEXT)
    @field:Schema(title = "Description", description = "What happens, in the customer's words")
    val description: String? = null,
    @field:NotNull
    @field:Schema(title = "Priority", description = PRIORITY, allowableValues = [LOW, NORMAL, HIGH, URGENT])
    val priority: String? = null,
)

@Schema(title = "Assign")
data class Assign(
    @field:NotNull
    // Its choices are the desk's agents on duty for the ticket's priority (Tickets.agentOnDuty).
    @field:Schema(title = "Agent", description = "The agent who works on the ticket", examples = ["priya"])
    val agent: String? = null,
)

@Schema(title = "Comment")
data class Comment(
    @field:NotNull @field:Size(min = 1, max = MAX_TEXT)
    @field:Schema(title = "Text", description = "The comment")
    val text: String? = null,
    @field:NotNull
    @field:Schema(title = "Internal", description = "True for the desk only; false for the customer to read too")
    val internal: Boolean? = null,
)

@Schema(title = "Ask the customer")
data class AskCustomer(
    @field:NotNull @field:Size(min = 1, max = MAX_TEXT)
    @field:Schema(title = "Question", description = "What the customer is asked", examples = ["Which browser?"])
    val question: String? = null,
)

@Schema(title = "Customer replies")
data class CustomerReplies(
    @field:NotNull @field:Size(min = 1, max = MAX_TEXT)
    @field:Schema(title = "Text", description = "The customer's reply")
    val text: String? = null,
)

@Schema(title = "Resolve")
data class Resolve(
    @field:NotNull @field:Size(min = 1, max = MAX_TEXT)
    @field:Schema(title = "Resolution", description = "How the problem was solved")
    val resolution: String? = null,
)

@Schema(title = "Reopen")
data class Reopen(
    @field:NotNull @field:Size(min = 1, max = MAX_TEXT)
    @field:Schema(title = "Reason", description = "Why the problem is not solved")
    val reason: String? = null,
)

@Schema(title = "Change the priority")
data class ChangePriority(
    @field:NotNull
    @field:Schema(title = "Priority", description = PRIORITY, allowableValues = [LOW, NORMAL, HIGH, URGENT])
    val priority: String? = null,
)

/** Closing takes no input. */
@Schema(title = "No input")
class NoInput
