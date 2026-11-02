// Task 3 with no model: add ", retired" to Frodo's current role. The role is read from his state, and decide { }
// works it out again from what is there after a stale refusal, so someone else's change is kept.
val entry by input(default = "http://localhost:8099/")

heart(entry) {
    act("staff:search-employees", "name" to "Frodo") {
        follow("item", "Frodo Baggins") {
            decide {
                act("staff:update", "firstName" to "Frodo", "lastName" to "Baggins", "role" to "${state["role"]}, retired")
            }
        }
    }
}
