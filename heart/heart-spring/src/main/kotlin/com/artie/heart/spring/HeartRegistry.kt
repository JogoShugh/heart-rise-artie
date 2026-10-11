package com.artie.heart.spring

import com.artie.heart.HeartResource
import com.artie.heart.Layers
import com.artie.heart.Profile
import org.springframework.stereotype.Component

/**
 * Every resource type this server declares, as the [HeartResource] beans it
 * registers. heart-spring serves all of them; a server writes no controllers.
 */
@Component
class HeartRegistry(val resources: List<HeartResource<*, *>>) {
    val layers: List<Layers<*, *>> = resources.map { it.layers }

    /** Address -> body, across every declared type, for each kind of content-addressed resource. */
    val affordanceSets: Map<String, String> = layers.flatMap { it.affordanceSets.entries }.associate { it.toPair() }
    val schemas: Map<String, String> = layers.flatMap { it.schemas.entries }.associate { it.toPair() }
    val explanations: Map<String, String> = layers.flatMap { it.explanations.entries }.associate { it.toPair() }

    /** Each declared profile, by name. Two types may share one profile. */
    val profiles: Map<String, Profile> = layers.map { it.type.profile }.associateBy { it.name }
}
