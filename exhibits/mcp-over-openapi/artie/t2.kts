// Task 2 with no model: change Frodo's role when the service offers it; otherwise say why it doesn't.
val entry by input(default = "http://localhost:8099/")

heart(entry) {
    act("staff:search-employees", "name" to "Frodo") {
        follow("item", "Frodo Baggins") {
            if ("staff:update" in held.offered) {
                act("staff:update", "firstName" to "Frodo", "lastName" to "Baggins", "role" to "ring bearer, retired")
            } else {
                note("staff:update is not offered: ${why("staff:update")}")
            }
        }
    }
}
