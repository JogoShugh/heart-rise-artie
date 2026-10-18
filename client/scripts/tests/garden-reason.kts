// A reason step against the garden: the script builds a bed, then says in words what it wants, and
// Claude chooses the act and its fields from what the new bed offers. ARTIE checks the choice against
// the form's schema before sending it. Needs --reasoner claude and a model key.
//   artie run tests/garden-reason.kts --reasoner claude
val entry by input(default = "coap://localhost:5683/")

heart(entry) {
    act("garden:build-bed", "label" to "Reasoned bed", "widthFt" to 4, "lengthFt" to 4) {
        reason("plant some basil in it, about two centimetres deep")
        check("cropName" eq "basil")
        note("the bed is ${state["status"]}, with ${state["cropName"]}")
    }
}
