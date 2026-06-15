package dev.flaticols.applecontainer.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import dev.flaticols.applecontainer.ContainerConnectionStore
import dev.flaticols.applecontainer.ContainerEngineModel

/**
 * "Apple Container…" in the Services view's + (Add Service) popup and the Tools
 * menu: adds the local engine connection and kicks off the first refresh.
 */
class AddConnectionAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible =
            project != null && !ContainerConnectionStore.getInstance(project).isAdded
    }

    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let(::addContainerConnection)
    }
}

/** Adds the local engine connection and kicks off the first refresh. */
fun addContainerConnection(project: Project) {
    ContainerConnectionStore.getInstance(project).add()
    val model = ContainerEngineModel.getInstance(project)
    model.structureChanged()
    model.refresh()
}
