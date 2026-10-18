// The garden task, scripted: every selection written down. Build a 4 by 4 herb bed, plant basil about
// 2 cm deep, water it with 250 ml per cell, and check the bed. No model.
//   artie run tests/garden-task-scripted.kts
val entry by input(default = "coap://localhost:5683/")

heart(entry) {
    act("garden:build-bed", "label" to "Herbs, scripted", "widthFt" to 4, "lengthFt" to 4) {
        act("garden:sow-seed", "seedType" to "basil", "depthCm" to 2)
        act("garden:water-cell", "milliliters" to 250)
        check(state["/cropName"] == "basil") { "the bed should hold basil" }
        check((state["/moistureLevel"] as Long) > 0) { "the bed should be watered" }
    }
}
