// Task 4 with no model: set Frodo's role, as the person said. Refused as stale, ARTIE won't send it again over a
// role someone else changed since it was read: it stops and says so.
val entry by input(default = "http://localhost:8099/")

heart(entry) {
    act("staff:search-employees", "name" to "Frodo") {
        follow("item", "Frodo Baggins") {
            act("staff:update", "firstName" to "Frodo", "lastName" to "Baggins", "role" to "ring bearer, retired")
        }
    }
}
