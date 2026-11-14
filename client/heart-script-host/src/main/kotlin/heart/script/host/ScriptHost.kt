package heart.script.host

import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.jsr223.KotlinJsr223DefaultScriptCompilationConfiguration
import kotlin.script.experimental.jsr223.KotlinJsr223DefaultScriptEvaluationConfiguration
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.jsr223.KotlinJsr223ScriptEngineImpl

/**
 * The Kotlin script engine a HEART script runs in. Its compiler has Power-assert, told to draw [CHECK]: a
 * condition that fails is shown with the value of each part (spec/ideas/script-checks-and-reason-blocks.md).
 */
object ScriptHost {
    /** The script's check, as Power-assert names it: the function, with no overload named. */
    const val CHECK = "heart.script.Here.check"

    private const val PLUGIN = "org/jetbrains/kotlin/powerassert/PowerAssertCompilerPluginRegistrar.class"
    private const val DRAW = "plugin:org.jetbrains.kotlin.powerassert:function=$CHECK"

    /**
     * A new engine: one per run, as every run starts afresh. [imports] are the compiler's default imports,
     * never lines put before the script, so a script's line numbers are its author's.
     */
    fun engine(imports: List<String>): ScriptEngine {
        val kotlin = checkNotNull(ScriptEngineManager().getEngineByExtension("kts")) { "no Kotlin script engine" }
        val compilation = ScriptCompilationConfiguration(KotlinJsr223DefaultScriptCompilationConfiguration) {
            jvm { dependenciesFromCurrentContext(wholeClasspath = true) }
            defaultImports(imports)
            compilerOptions.append("-Xplugin=${plugin()}", "-P", DRAW)
        }
        return KotlinJsr223ScriptEngineImpl(
            kotlin.factory, compilation, KotlinJsr223DefaultScriptEvaluationConfiguration,
        ) { null }
    }

    /** Where Power-assert's jar is, on this class path. */
    private fun plugin(): String {
        val found = checkNotNull(ScriptHost::class.java.classLoader.getResource(PLUGIN)) {
            "no Power-assert on the class path"
        }
        return found.toString().removePrefix("jar:file:").substringBefore("!/")
    }
}
