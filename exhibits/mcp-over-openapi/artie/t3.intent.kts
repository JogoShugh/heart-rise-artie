// Task 3 in a person's words, step by step: the goal's own steps, each an act(intent = …) the reasoner chooses
// from what the service offers at that step. "His current role" is kept as said, read when the step runs.
// No planner. Needs --reasoner.
val entry by input(default = "http://localhost:8099/")

heart(entry) {
    act(intent = "Find the employees named Baggins") {
        act(intent = "Open Frodo Baggins") {
            act(intent = """Add ", retired" to the end of his current role, keeping the rest of it as it is""")
        }
    }
}
