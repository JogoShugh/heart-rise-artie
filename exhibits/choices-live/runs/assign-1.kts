// Choices from another resource, live (HAL-SCHEMA-FORMS-HR Rule 8). The tickets desk's assign act takes its
// agent from /agents{?priority}, filled from the ticket's own priority, where onDuty is true. Ticket 1 is high:
// Priya and Marco handle it and are on duty; Lena handles it but is off duty. ARTIE reads /agents for the
// choices; Claude is shown them as a closed set. Then the ticket goes urgent, which Marco does not handle, and the
// choices follow. Run on a reset desk:
//   ../../domains/tickets/reset-db.sh
//   artie run assign.kts --reasoner claude --trace runs/assign-1.yaml
val entry by input(default = "http://localhost:8282/")

heart(entry) {
    act("support:get-ticket", "id" to 1) {
        try {
            act("support:assign", "agent" to "lena")
        } catch (stopped: ScriptStopped) {
            note("by script, for Lena: ${stopped.reason}")
        }
        try {
            act(intent = "assign this ticket to Lena")
        } catch (stopped: ScriptStopped) {
            note("in words, for Lena: ${stopped.reason}")
        }
        act(intent = "assign this ticket to someone who can take it now")
        note("assigned to ${state["agent"]}")
        act("support:change-priority", "priority" to "urgent")
        act(intent = "hand this urgent ticket to someone other than its current agent who can take it now")
        note("assigned to ${state["agent"]}")
    }
}
