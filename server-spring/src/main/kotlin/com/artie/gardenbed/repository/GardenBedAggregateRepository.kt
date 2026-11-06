package com.artie.gardenbed.repository

import com.artie.gardenbed.domain.GardenBedAggregate
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

/** The beds' identities and labels. Their events are in the HEART event store. */
interface GardenBedAggregateRepository : CoroutineCrudRepository<GardenBedAggregate, Long>
