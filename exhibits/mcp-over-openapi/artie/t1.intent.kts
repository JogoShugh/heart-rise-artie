// Task 1 in a person's words, step by step: the goal's own steps, each an act(intent = …) the reasoner chooses
// from what the service offers at that step. No planner. Needs --reasoner.
val entry by input(default = "http://localhost:8099/")

heart(entry) {
    act(intent = "Hire Samwise Gamgee as a gardener") {
        act(intent = """Change his role to "gardener, mayor"""")
    }
    act(intent = "Find the employees named Baggins") {
        act(intent = "Open Bilbo Baggins") {
            act(intent = "Dismiss him")
        }
    }
}
