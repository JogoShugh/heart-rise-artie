// The garden task, hybrid: the script builds and waters, exactly; the model makes the one choice that
// is a matter of judgement, what to plant for pesto and how deep. Needs --reasoner claude.
//   artie run tests/garden-task-hybrid.kts --reasoner claude
val entry by input(default = "coap://localhost:5683/")

heart(entry) {
    act("garden:build-bed", "label" to "Herbs, hybrid", "widthFt" to 4, "lengthFt" to 4) {
        reason("plant the herb that pesto is made from, at a sensible depth")
        act("garden:water-cell", "milliliters" to 250)
        check("cropName" eq "basil", "moistureLevel" above 0)
    }
}
