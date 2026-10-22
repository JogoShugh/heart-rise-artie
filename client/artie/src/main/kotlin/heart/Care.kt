package heart

/**
 * What ARTIE takes care over on the wire, each a switch a run may turn off (spec/ideas/off-the-rails.md).
 *
 * - [checkedLinks]: forms links and schema references are followed only with an integrity value; false for older
 *   servers that give none.
 * - [validators]: each act carries the validator held; false sends blind writes, which only a server's development
 *   switch takes.
 */
class Care(val checkedLinks: Boolean = true, val validators: Boolean = true)
