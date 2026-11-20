// Task 1 with no model: what a person who read the service once would write.
val entry by input(default = "http://localhost:8099/")

heart(entry) {
    act("staff:hire", "firstName" to "Samwise", "lastName" to "Gamgee", "role" to "gardener") {
        act("staff:update", "firstName" to "Samwise", "lastName" to "Gamgee", "role" to "gardener, mayor")
    }
    act("staff:search-employees", "name" to "Bilbo") {
        follow("item", "Bilbo Baggins") {
            act("staff:dismiss")
        }
    }
}
