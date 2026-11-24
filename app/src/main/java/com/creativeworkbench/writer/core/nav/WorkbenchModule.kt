package com.creativeworkbench.writer.core.nav

data class NavEntry(
    val id: String,
    val label: String,
    val route: String
)

sealed interface Slot<T> {
    data object Nav : Slot<NavEntry>
}

class Disposable(private val onDispose: () -> Unit) {
    private var disposed = false
    fun dispose() {
        if (!disposed) {
            disposed = true
            onDispose()
        }
    }
}

class ModuleHost {
    private val nav = ArrayList<NavEntry>()

    val navEntries: List<NavEntry> get() = nav.toList()

    fun <T> register(slot: Slot<T>, item: T): Disposable {
        when (slot) {
            Slot.Nav -> {
                val entry = item as NavEntry
                nav.add(entry)
                return Disposable { nav.remove(entry) }
            }
        }
    }

    fun disposeAll() {
        nav.clear()
    }
}

interface WorkbenchModule {
    val id: String
    val navEntries: List<NavEntry>
    fun register(host: ModuleHost): List<Disposable>
}
