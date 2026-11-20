package com.artie.heart.sims

import com.fasterxml.jackson.databind.JsonNode

/**
 * The faults a control request sets, by name, as JSON: `{"applied": "forms=link",
 * "noProfile": true, "reshaped": {"github:comment": {"method": null}}}`. A name
 * left out changes nothing; one that is not here is refused.
 */
object FaultSettings {
    private val settings: Map<String, (Faults, JsonNode) -> Unit> = mapOf(
        "applied" to { f, v -> f.applied = v.asText() },
        "mediaType" to { f, v -> f.mediaType = v.asText() },
        "onlyAccept" to { f, v -> f.onlyAccept = v.takeUnless { it.isNull }?.asText() },
        "readStatus" to { f, v -> f.readStatus = v.asInt() },
        "noValidator" to { f, v -> f.noValidator = v.asBoolean() },
        "noProfile" to { f, v -> f.noProfile = v.asBoolean() },
        "actsWithNoBody" to { f, v -> f.actsWithNoBody = v.asBoolean() },
        "actsFrom" to { f, v -> f.actsFrom = v.asText() },
        "wrongIntegrity" to { f, v -> f.body.wrongIntegrity = v.asBoolean() },
        "noIntegrity" to { f, v -> f.body.noIntegrity = v.asBoolean() },
        "noSet" to { f, v -> f.body.noSet = v.asBoolean() },
        "oneAddress" to { f, v -> f.body.oneAddress = v.asBoolean() },
        "members" to { f, v -> f.body.members = v },
        "linksOnly" to { f, v -> f.body.linksOnly = v },
        "embedsOnly" to { f, v -> f.body.embedsOnly = v },
        "profileLink" to { f, v -> f.body.profileLink = v.asText() },
        "reshaped" to { f, v -> v.properties().forEach { (key, patch) -> f.body.reshaped[key] = patch } },
        "setLink" to { f, v -> f.body.setLink = SetLink(v["rel"].asText(), v["curie"]?.asText(), v["href"]?.asText()) },
    )

    fun apply(faults: Faults, wanted: JsonNode) = wanted.properties().forEach { (name, value) ->
        val setting = settings[name] ?: throw IllegalArgumentException("no fault $name")
        setting(faults, value)
    }
}
