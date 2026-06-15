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

    companion object {
        fun getInstance(): ContainerSettings = service()
    }

    class State {
        /** Empty means "auto-detect on PATH". */
        var binaryPath: String = ""

        /** Whether the Dockerfile gutter menu also offers Docker's own run options. */
        var dockerfileGutterCombined: Boolean = true

        /** Whether stopping a run also stops the buildkit build daemon. */
        var stopBuilderOnStop: Boolean = false
    }

    private var state = State()

    var binaryPath: String
        get() = state.binaryPath
        set(value) {
            state.binaryPath = value
        }

    var dockerfileGutterCombined: Boolean
        get() = state.dockerfileGutterCombined
        set(value) {
            state.dockerfileGutterCombined = value
        }

    var stopBuilderOnStop: Boolean
        get() = state.stopBuilderOnStop
        set(value) {
            state.stopBuilderOnStop = value
        }

    override fun getState(): State = state
    override fun loadState(state: State) {
        this.state = state
    }
}
