// Build a bed in the garden, sow it, and show what the garden refuses: a second sowing (not
// offered once planted) and a bed with no width (invalid input). The entry chooses the binding:
// the same script runs over CoAP or HTTP, unchanged.
//   artie run tests/garden-bed.kts
//   artie run tests/garden-bed.kts entry=http://localhost:8080/
val entry by input(default = "coap://localhost:5683/")
val name by input(default = "Scripted bed")
val seed by input(default = "basil")

heart(entry) {
    expect(InvalidInput) { act("garden:build-bed", "label" to name, "widthFt" to 0, "lengthFt" to 8) }
    expect(NewResource) {
        act("garden:build-bed", "label" to name, "widthFt" to 4, "lengthFt" to 8) {
            check(state["/status"] == "EMPTY") { "a new bed should be empty" }
            act("garden:sow-seed", "seedType" to seed, "depthCm" to 2)
            check(state["/cropName"] == seed) { "the bed should hold $seed" }
            expect(NotOffered) { act("garden:sow-seed", "seedType" to "mint") }
            note("offered once planted: ${held.offered}")
        }
    }
}
