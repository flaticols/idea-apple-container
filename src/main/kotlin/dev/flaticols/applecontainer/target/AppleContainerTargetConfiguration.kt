package dev.flaticols.applecontainer.target

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent

/**
 * Persisted state of an Apple Container run target: which machine to run in, and
 * the project root path on the target (identical to the host path thanks to the
 * virtio-fs home mount, so usually left empty and resolved at run time).
 */
class AppleContainerTargetConfiguration :
    TargetEnvironmentConfiguration(TYPE_ID),
    PersistentStateComponent<AppleContainerTargetConfiguration.State> {

    companion object {
        const val TYPE_ID = "AppleContainer"
    }

    class State : BaseState() {
        var machineId by string()
        var projectRoot by string()
    }

    private var state = State()

    override fun getState(): State = state
    override fun loadState(state: State) {
        this.state.copyFrom(state)
    }

    var machineId: String?
        get() = state.machineId
        set(value) {
            state.machineId = value
        }

    override var projectRootOnTarget: String
        get() = state.projectRoot.orEmpty()
        set(value) {
            state.projectRoot = value
        }
}
