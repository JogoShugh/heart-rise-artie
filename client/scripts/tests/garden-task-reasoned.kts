// The garden task, reasoned: each move said in words; the model chooses the form and its fields from
// what the garden offers, and ARTIE checks each choice before sending it. Needs --reasoner claude.
//   artie run tests/garden-task-reasoned.kts --reasoner claude
val entry by input(default = "coap://localhost:5683/")

heart(entry) {
    act(intent = "build a 4 by 4 foot herb bed called \"Herbs, reasoned\"") {
        act(intent = "plant basil in it, about 2 centimetres deep")
        act(intent = "water it with 250 millilitres per cell")
        check("cropName" eq "basil", "moistureLevel" above 0)
    }
}
