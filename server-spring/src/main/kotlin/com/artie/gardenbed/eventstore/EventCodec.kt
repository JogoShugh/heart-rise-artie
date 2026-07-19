package com.artie.gardenbed.eventstore

import com.artie.gardenbed.domain.GardenBedEvent
import com.artie.gardenbed.domain.GardenBedEventRow
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.r2dbc.postgresql.codec.Json
import org.springframework.stereotype.Component

/**
 * Deliberately hand-written per event type rather than relying on
 * Jackson's polymorphic (@JsonTypeInfo) machinery -- keeps the actual
 * stored JSON clean (just the event's own fields, matching exactly
 * what's in the seed data in V1__init.sql: {"seedType": "tomato",
 * "depthCm": 3}, not Jackson-injected type-discriminator noise), and
 * keeps the encode/decode logic fully explicit and easy to extend when
 * you add a new event type later.
 */
@Component
class EventCodec(private val objectMapper: ObjectMapper) {

    fun encode(event: GardenBedEvent): Pair<String, Json> {
        val (typeName, fields) = when (event) {
            is GardenBedEvent.SeedSown -> "SeedSown" to objectMapper.createObjectNode().apply {
                put("seedType", event.seedType)
                if (event.depthCm != null) put("depthCm", event.depthCm) else putNull("depthCm")
            }
            is GardenBedEvent.CellWatered -> "CellWatered" to objectMapper.createObjectNode().apply {
                put("milliliters", event.milliliters)
            }
            is GardenBedEvent.BedHarvested -> "BedHarvested" to objectMapper.createObjectNode().apply {
                put("yieldGrams", event.yieldGrams)
                put("cropName", event.cropName)
            }
        }
        return typeName to Json.of(objectMapper.writeValueAsString(fields))
    }

    fun decode(row: GardenBedEventRow): GardenBedEvent {
        val node = objectMapper.readTree(row.payload.asString()) as ObjectNode
        return when (row.eventType) {
            "SeedSown" -> GardenBedEvent.SeedSown(
                gardenBedId = row.gardenBedId,
                sequenceNo = row.sequenceNo,
                seedType = node.get("seedType").asText(),
                depthCm = node.get("depthCm")?.takeIf { !it.isNull }?.asInt(),
                occurredAt = row.occurredAt
            )
            "CellWatered" -> GardenBedEvent.CellWatered(
                gardenBedId = row.gardenBedId,
                sequenceNo = row.sequenceNo,
                milliliters = node.get("milliliters").asInt(),
                occurredAt = row.occurredAt
            )
            "BedHarvested" -> GardenBedEvent.BedHarvested(
                gardenBedId = row.gardenBedId,
                sequenceNo = row.sequenceNo,
                yieldGrams = node.get("yieldGrams").asInt(),
                cropName = node.get("cropName").asText(),
                occurredAt = row.occurredAt
            )
            else -> error("Unknown event_type '${row.eventType}' for garden_bed_event row ${row.id} -- " +
                "this means an event was written by a version of this code that supported a type " +
                "this version doesn't know how to decode. In a real system this is exactly the kind " +
                "of thing that needs an explicit event-schema-versioning strategy (upcasting old " +
                "event shapes to new ones at read time) -- not handled here, flagged in the README.")
        }
    }

    /** Only used when persisting a fresh event before it has a real row id. */
    fun toRow(event: GardenBedEvent): GardenBedEventRow {
        val (typeName, payload) = encode(event)
        return GardenBedEventRow(
            gardenBedId = event.gardenBedId,
            sequenceNo = event.sequenceNo,
            eventType = typeName,
            payload = payload,
            occurredAt = event.occurredAt
        )
    }
}

/** Small helper so payload snapshots (GardenBedState -> JSONB) reuse
 *  the same ObjectMapper rather than each caller wiring its own. Used
 *  by EventStore when writing/reading garden_bed_snapshot rows. */
fun ObjectMapper.toJson(value: Any): Json = Json.of(this.writeValueAsString(value))

fun ObjectMapper.fromJson(json: Json, clazz: Class<*>): Any =
    this.readValue(json.asString(), clazz)
