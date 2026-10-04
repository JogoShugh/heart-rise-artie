package com.artie.gardenbed.web.dto

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.ResolvableType
import org.springframework.hateoas.mediatype.PropertyUtils

/**
 * The HAL-FORMS dialect builds each `_templates` entry by reflecting over
 * the request DTO with Spring HATEOAS's PropertyUtils. A Kotlin `val`
 * has a getter but no setter, which PropertyUtils reports as readOnly --
 * and HAL-FORMS then tells clients the field cannot be filled in
 * (Spring HATEOAS issue #1310). journal.html entry 02 catches this live;
 * this test catches it without a running server.
 */
class RequestDtoMetadataTest {

    private fun assertAllWritable(type: Class<*>, expected: Set<String>) {
        val props = PropertyUtils.getExposedProperties(ResolvableType.forClass(type))
            .stream().toList()
        val names = props.map { it.name }.toSet()
        assertTrue(names.containsAll(expected), "${type.simpleName} exposes $names, expected $expected")
        for (p in props) {
            assertFalse(p.isReadOnly, "${type.simpleName}.${p.name} is readOnly; HAL-FORMS would mark it unfillable")
        }
    }

    @Test
    fun `SowSeedRequest fields are writable in HAL-FORMS`() =
        assertAllWritable(SowSeedRequest::class.java, setOf("seedType", "depthCm"))

    @Test
    fun `WaterCellRequest fields are writable in HAL-FORMS`() =
        assertAllWritable(WaterCellRequest::class.java, setOf("milliliters"))
}
