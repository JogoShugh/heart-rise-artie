# heart-eventsourcing

Optional storage for state kept as events. A resource's state is the fold
of its events, in order; the number of the last one is its version.

## Use

Declare how a type is event-sourced:

```kotlin
val GARDEN_BEDS = EventSourcing<GardenBedEvent, GardenBedState>(
    stream = "garden-bed",
    types = mapOf("SeedSown" to SeedSown::class.java, "CellWatered" to CellWatered::class.java),
    stateClass = GardenBedState::class.java,
    empty = { id -> GardenBedState.empty(id, "") },
    apply = GardenBedProjector::apply,      // (S, Recorded<E>) -> S
)
```

Then use an `EventStore`:

| Call | Result |
|---|---|
| `append(spec, id, sequenceNo, event)` | Stores the event. A number already taken throws `ConcurrentWriteException` (heart-core turns it into 412) |
| `nextSequenceNo(spec, id)` | The number the next event takes |
| `state(spec, id, asOf = null)` | The folded state, now or as of an instant |
| `history(spec, id)` | Every event, as `Recorded` (number, time, event) |
| `forget(stream)` | Deletes a stream; for tests and demos |

Events are plain data; the store records which resource, which number and
when. A snapshot is kept every `snapshotEvery` events (5 by default) and
never changes an answer.

## Stores

- `InMemoryEventStore`: for tests and demos.
- `PostgresEventStore(DatabaseClient)`: R2DBC. The UNIQUE constraint on
  `(stream, stream_id, sequence_no)` is the concurrency control. Tables are in
  `src/main/resources/com/artie/heart/eventsourcing/schema.sql`; apply them in
  your own migration (see the garden's `V2__heart_event_store.sql`).

## Tests

`event-store.feature` runs every scenario against both stores, Postgres in
Testcontainers (needs Docker).
