package com.artie.gardenbed.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

/**
 * bdd-gates: the domain boundary as a failing test. The domain folds events
 * into state; it knows nothing about HTTP, hypermedia, or the layers above it.
 * (Spring Data mapping annotations on GardenBedAggregate are tolerated.)
 */
class ArchitectureBoundaryTest {

    private val forbiddenPrefixes = listOf(
        "org.springframework.web",
        "org.springframework.http",
        "org.springframework.hateoas",
        "com.artie.gardenbed.declaration",
        "com.artie.gardenbed.service",
        "com.artie.gardenbed.eventstore",
    )

    @Test
    fun `domain imports no web, service, or storage types`() {
        Konsist.scopeFromProduction()
            .files
            .filter { it.hasPackage("com.artie.gardenbed.domain..") }
            .assertFalse { file ->
                file.hasImport { import -> forbiddenPrefixes.any { import.name.startsWith(it) } }
            }
    }

    /**
     * spec/PLAN-HEART-LIBRARY-DOMAINS-EVAL.md, A4 rule 3: a domain server
     * declares its resources and the HEART library serves them, so no garden
     * code touches HTTP, hypermedia rendering or CoAP.
     */
    @Test
    fun `no garden code touches HTTP, hypermedia rendering or CoAP`() {
        val wire = listOf(
            "org.springframework.web",
            "org.springframework.http",
            "org.springframework.hateoas",
            "org.eclipse.californium",
        )
        Konsist.scopeFromProduction()
            .files
            .filter { it.hasPackage("com.artie.gardenbed..") }
            .assertFalse { file -> file.hasImport { import -> wire.any { import.name.startsWith(it) } } }
    }
}
