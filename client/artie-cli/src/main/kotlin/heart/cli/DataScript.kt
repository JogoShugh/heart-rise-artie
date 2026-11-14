package heart.cli

import heart.cli.ArtieCli.Ended
import heart.script.Run
import heart.script.ScriptStopped
import java.nio.file.Path

/*
 * A script held as data, NAME.heart.yaml (spec/PLAN-KEPT-SCRIPTS-AS-DATA.md, step 4): run with no script engine and
 * no compile, each step made through the functions a Kotlin script calls. Its lock file and config are NAME's, as a
 * Kotlin script's are.
 */

private const val DATA = ".heart.yaml"

/** Whether [file] is a script held as data. */
internal fun isData(file: Path): Boolean = file.fileName.toString().endsWith(DATA)

/** The name a script's lock file and config are named after: NAME of NAME.kts, or of NAME.heart.yaml. */
internal fun scriptName(file: Path): String = scriptName(file.fileName.toString())

/** NAME of NAME.kts or NAME.heart.yaml, its own dots kept (`t3.kept` of `t3.kept.heart.yaml`). */
fun scriptName(name: String): String =
    if (name.endsWith(DATA)) name.removeSuffix(DATA) else name.substringBeforeLast('.')

/** [script], held as data, made in [run]; a stop is said as [stopped] says it. */
internal fun held(run: Run, script: String, stopped: (ScriptStopped) -> Ended): Ended {
    Run.current = run
    val data = runCatching { org.yaml.snakeyaml.Yaml().load<Any?>(script) as Map<*, *> }
        .getOrElse { throw BadArguments("$STOPPED: not a script held as data: ${it.message}") }
    return try {
        heart.script.runData(data)
        finished(run)
    } catch (e: ScriptStopped) {
        stopped(e)
    }
}
