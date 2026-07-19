package com.artie.gardenbed.config

import org.springframework.context.annotation.Configuration
import org.springframework.hateoas.config.EnableHypermediaSupport
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType

/**
 * This one annotation is what gives the halforms dialect real, native
 * Spring HATEOAS support -- it registers the Jackson modules that know
 * how to render EntityModel + Affordances as spec-legal HAL-FORMS
 * _templates, with zero hand-written JSON construction for that
 * dialect. See GardenBedController for where Affordances actually get
 * attached to links.
 *
 * There is deliberately NO equivalent annotation for the jsonschema
 * (jbadeau/hal-schema-forms) dialect -- Spring HATEOAS doesn't ship one,
 * and I found no evidence of an official jbadeau integration for it
 * either (see the project README for what I actually searched and
 * found). That dialect is hand-built in JsonSchemaDialectSupport.kt,
 * on the same underlying domain data, rather than faked as if it had
 * equivalent framework-native support.
 */
@Configuration
@EnableHypermediaSupport(type = [HypermediaType.HAL_FORMS])
class HateoasConfig
