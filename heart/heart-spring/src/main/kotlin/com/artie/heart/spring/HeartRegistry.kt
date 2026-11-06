package com.artie.heart.spring

import com.artie.heart.HeartResource
import com.artie.heart.Layers
import com.artie.heart.Profile
import com.artie.heart.SharedContent
import org.springframework.stereotype.Component

/**
 * Every resource type this server declares, as the [HeartResource] beans it
 * registers. heart-spring serves all of them; a server writes no controllers.
 */
@Component
class HeartRegistry(val resources: List<HeartResource<*, *>>) {
    val layers: List<Layers<*, *>> = resources.map { it.layers }

    /** Address -> body, across every declared type, for each kind of content-addressed resource. */
    val content: SharedContent = SharedContent(layers)

    /** Each declared profile, by name. Two types may share one profile. */
    val profiles: Map<String, Profile> = layers.map { it.type.profile }.associateBy { it.name }
}
