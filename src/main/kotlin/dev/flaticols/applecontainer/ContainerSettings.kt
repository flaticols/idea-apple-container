package dev.flaticols.applecontainer

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * Application-level settings. Only an optional override for the `container`
 * binary path — by default it is auto-detected on PATH (see [ContainerCli]).
 */
@Service(Service.Level.APP)
@State(name = "AppleContainerSettings", storages = [Storage("appleContainer.xml")])
class ContainerSettings : PersistentStateComponent<ContainerSettings.State> {

    class State {
        /** Empty means "auto-detect on PATH". */
        var binaryPath: String = ""
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var binaryPath: String
        get() = state.binaryPath
        set(value) {
            state.binaryPath = value
        }

    companion object {
        fun getInstance(): ContainerSettings = service()
    }
}
