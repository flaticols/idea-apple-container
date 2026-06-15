package dev.flaticols.applecontainer

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Whether the "Apple Container" connection has been added to this project's
 * Services view (Docker-style add/remove). There is a single local engine, so
 * this is just a presence flag plus a display name, persisted per project in
 * `.idea/appleContainer.xml`.
 */
@Service(Service.Level.PROJECT)
@State(name = "AppleContainerConnection", storages = [Storage("appleContainer.xml")])
class ContainerConnectionStore : PersistentStateComponent<ContainerConnectionStore.State> {

    class State {
        var added: Boolean = false
        var name: String = "Apple Container"
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    val isAdded: Boolean get() = state.added
    val name: String get() = state.name

    fun add() {
        state.added = true
    }

    fun remove() {
        state.added = false
    }

    companion object {
        fun getInstance(project: Project): ContainerConnectionStore = project.service()
    }
}
