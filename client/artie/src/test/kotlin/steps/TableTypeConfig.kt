package steps

import io.cucumber.java.DefaultDataTableEntryTransformer
import java.lang.reflect.Type
import kotlin.reflect.full.primaryConstructor

class TableTypeConfig {
    @DefaultDataTableEntryTransformer
    fun transform(entry: Map<String, String>, type: Type): Any {
        val klass = (type as Class<*>).kotlin
        val ctor = klass.primaryConstructor
            ?: error("${klass.simpleName} needs a primary constructor")
        val args = ctor.parameters.associateWith { p ->
            val raw = entry[p.name] ?: error("DataTable row missing '${p.name}' column")
            when (p.type.classifier) {
                Boolean::class -> raw.toBoolean()
                Int::class -> raw.toInt()
                Long::class -> raw.toLong()
                Double::class -> raw.toDouble()
                else -> raw
            }
        }
        return ctor.callBy(args)
    }
}
