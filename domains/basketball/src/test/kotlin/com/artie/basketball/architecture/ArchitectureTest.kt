package com.artie.basketball.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

/**
 * A domain server declares its resources and the HEART library serves them
 * (spec/PLAN-HEART-LIBRARY-DOMAINS-EVAL.md, A4 rule 3). So no basketball code
 * touches HTTP, hypermedia rendering or CoAP, and the domain knows nothing of
 * the declaration or the storage around it.
 */
class ArchitectureTest {

    @Test
    fun `no basketball code touches HTTP, hypermedia rendering or CoAP`() {
        val wire = listOf(
            "org.springframework.web", "org.springframework.http",
            "org.springframework.hateoas", "org.eclipse.californium",
        )
        Konsist.scopeFromProduction().files
            .assertFalse { file -> file.hasImport { import -> wire.any { import.name.startsWith(it) } } }
    }

    @Test
    fun `the domain knows nothing of the declaration or the storage`() {
        val outside = listOf(
            "com.artie.basketball.declaration", "com.artie.basketball.store", "com.artie.basketball.config",
        )
        Konsist.scopeFromProduction().files
            .filter { it.hasPackage("com.artie.basketball.domain..") }
            .assertFalse { file -> file.hasImport { import -> outside.any { import.name.startsWith(it) } } }
    }
}
