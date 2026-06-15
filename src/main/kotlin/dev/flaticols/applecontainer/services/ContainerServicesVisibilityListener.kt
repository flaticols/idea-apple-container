package dev.flaticols.applecontainer.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import dev.flaticols.applecontainer.ContainerEngineModel

/**
 * Drives the engine's background poll off the Services tool window's visibility:
 * polling runs only while the panel is open, so a hidden Services view spawns no
 * `container` processes. Registered via `<projectListeners>` in plugin.xml
 * (the platform injects the [Project] into the constructor).
 */
class ContainerServicesVisibilityListener(private val project: Project) : ToolWindowManagerListener {

    override fun stateChanged(toolWindowManager: ToolWindowManager) {
        if (project.isDisposed) return
        val visible = toolWindowManager.getToolWindow(ToolWindowId.SERVICES)?.isVisible == true
        ContainerEngineModel.getInstance(project).setPollingActive(visible)
    }
}
