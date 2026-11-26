package com.creativeworkbench.writer.core.nav

object ModuleRegistry {
    val all: List<WorkbenchModule> = listOf(NotesModule)
}

class ModuleRuntime(private val host: ModuleHost) {
    fun assemble(enabled: Set<String>) {
        host.disposeAll()
        ModuleRegistry.all
            .filter { enabled.contains(it.id) }
            .forEach { it.register(host) }
    }
}
