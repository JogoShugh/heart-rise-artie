package com.artie.heart.spring

import com.artie.heart.ActInput
import com.artie.heart.Layers

/** What an act left behind: the resource's new state, and any headers the act adds to its answer. */
data class Acted<S>(val state: S, val headers: Map<String, String> = emptyMap())

/**
 * A declared resource type, as a server registers it with heart-spring: the
 * [layers] rendered from its declaration, how one resource is read ([load],
 * which throws [com.artie.heart.ResourceNotFoundException] for an unknown id),
 * and what each act does ([perform], given the act's name and its checked
 * body). heart-spring serves the rest: Layer 1 at `/{collection}/{id}`, the
 * acts at `/{collection}/{id}/actions/{path}`, and Layers 2 and 3.
 */
class HeartResource<S, K>(
    val layers: Layers<S, K>,
    val load: suspend (Long) -> S,
    val perform: suspend (id: Long, act: String, input: ActInput) -> Acted<S>,
)
