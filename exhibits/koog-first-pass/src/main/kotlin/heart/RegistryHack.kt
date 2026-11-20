package heart

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import java.lang.reflect.Field

object RegistryHack {
    // Cache the field so you don't keep looking it up
    private val toolsField: Field = ToolRegistry::class.java.getDeclaredField("_tools").apply {
        isAccessible = true
    }

    @Suppress("UNCHECKED_CAST")
    fun clearRegistry(registry: ToolRegistry) {
        val toolsList = toolsField.get(registry) as MutableList<Tool<*, *>>
        toolsList.clear()
    }

    @Suppress("UNCHECKED_CAST")
    fun updateRegistry(registry: ToolRegistry, newTools: List<Tool<*, *>>) {
        val toolsList = toolsField.get(registry) as MutableList<Tool<*, *>>
        toolsList.clear()
        toolsList.addAll(newTools)
    }
}

// In a dedicated file like RegistryExtensions.kt
internal fun ToolRegistry.update(newTools: List<Tool<*, *>>) {
    RegistryHack.updateRegistry(this, newTools)
}

// In a dedicated file like RegistryExtensions.kt
internal fun ToolRegistry.clear() {
    RegistryHack.clearRegistry(this)
}
