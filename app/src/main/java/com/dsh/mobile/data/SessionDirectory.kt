package com.dsh.mobile.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A snapshot belongs to one authenticated connection, even while no page observes it. */
internal class SessionDirectory(connection: DshConnection) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val sessions = DirectoryEntry(scope, connection::listSessions)
    val workspace = DirectoryEntry(scope, connection::workspaceList)
    val presets = DirectoryEntry(scope, connection::agentPresets)

    fun archive(id: String, archived: Boolean) = workspace.edit { snapshot ->
        val ids = snapshot.archivedSessionIds
        snapshot.copy(archivedSessionIds = if (archived) (ids + id).distinct() else ids - id)
    }

    fun close() = scope.cancel()
}

internal data class DirectorySnapshot<T>(val value: T? = null, val error: Exception? = null, val loading: Boolean = false)

internal class DirectoryEntry<T : Any>(private val scope: CoroutineScope, private val fetch: suspend () -> T) {
    private val mutable = MutableStateFlow(DirectorySnapshot<T>())
    val state = mutable.asStateFlow()
    private var observed = false
    private var revision = 0L

    @Synchronized fun load(force: Boolean = false) {
        observed = true
        if (!force && (mutable.value.loading || mutable.value.value != null || mutable.value.error != null)) return
        revision++
        if (mutable.value.loading) return
        mutable.value = mutable.value.copy(loading = true, error = null)
        scope.launch {
            while (isActive) {
                val before = synchronized(this@DirectoryEntry) { revision }
                val result = try {
                    DirectorySnapshot(value = fetch())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DirectorySnapshot(value = mutable.value.value, error = e)
                }
                synchronized(this@DirectoryEntry) {
                    if (revision == before) {
                        mutable.value = result
                        return@launch
                    }
                }
            }
        }
    }

    @Synchronized fun invalidate() {
        if (observed) load(force = true)
    }

    @Synchronized fun edit(transform: (T) -> T) {
        val current = mutable.value
        current.value?.let { mutable.value = current.copy(value = transform(it)) }
        // A read started before this event must never overwrite the newer snapshot.
        if (current.loading) revision++
    }
}
