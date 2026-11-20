// Task 2 in a person's words, step by step: the goal's own steps, each an act(intent = …) the reasoner chooses
// from what the service offers at that step. No planner. Needs --reasoner.
val entry by input(default = "http://localhost:8099/")

heart(entry) {
    act(intent = "Find the employees named Baggins") {
        act(intent = "Open Frodo Baggins") {
            act(intent = """Change his role to "ring bearer, retired"""")
        }
    }
}
