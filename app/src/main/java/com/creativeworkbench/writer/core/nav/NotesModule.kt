package com.creativeworkbench.writer.core.nav

object NotesModule : WorkbenchModule {
    override val id: String = "notes"
    override val navEntries: List<NavEntry> = listOf(
        NavEntry(id = "notes", label = "笔记", route = "notes")
    )

    override fun register(host: ModuleHost): List<Disposable> =
        navEntries.map { host.register(Slot.Nav, it) }
}
