package com.artie.gardenbed.web

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import java.net.URI

/**
 * SPEC §6.2 (decided 2026-10-10): every refusal of an act is an RFC 9457
 * problem details object. The `type` URI names the refusal, so a client tells
 * refusals apart without reading `title` or `detail`.
 */
object Problems {
    const val BASE = "https://github.com/jogoshugh/heart-rise/problems/"

    fun respond(
        status: HttpStatus,
        type: String,
        title: String,
        detail: String?,
        members: Map<String, Any> = emptyMap(),
    ): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(status, detail)
        problem.type = URI.create(BASE + type)
        problem.title = title
        members.forEach { (name, value) -> problem.setProperty(name, value) }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem)
    }
}
