package heart

/**
 * What ARTIE takes care over on the wire, each a switch a run may turn off (spec/ideas/off-the-rails.md).
 *
 * - [checkedLinks]: forms links and schema references are followed only with an integrity value; false for older
 *   servers that give none.
 * - [validators]: each act carries the validator held; false sends blind writes, which only a server's development
 *   switch takes.
 * - [checkedBodies]: each act's fields are checked against its form's declared schema before it is sent (SPEC §6.3);
 *   false sends them as they are, for the server to check, as a run off the rails does.
 */
class Care(val checkedLinks: Boolean = true, val validators: Boolean = true, val checkedBodies: Boolean = true)
