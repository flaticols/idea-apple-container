package dev.flaticols.applecontainer.services

import com.intellij.execution.services.ServiceViewContributor
import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.execution.services.ServiceViewProvidingContributor
import com.intellij.execution.services.SimpleServiceViewDescriptor
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.navigation.ItemPresentation
import dev.flaticols.applecontainer.ContainerConnectionStore
import dev.flaticols.applecontainer.ContainerEngineModel
import dev.flaticols.applecontainer.ContainerIcons
import dev.flaticols.applecontainer.actions.AddConnectionAction
import dev.flaticols.applecontainer.actions.RunContainerDialog
import dev.flaticols.applecontainer.model.ContainerInfo
import dev.flaticols.applecontainer.model.EngineSnapshot
import dev.flaticols.applecontainer.model.ImageInfo
import dev.flaticols.applecontainer.ui.DetailPanels
import java.awt.datatransfer.StringSelection
import javax.swing.JComponent

/**
 * Contributes the "Apple Container" connection to the platform Services tool
 * window. Tree shape: connection → {Containers, Images} → entities. Data comes
 * from [ContainerEngineModel]; actions delegate to it.
 */
class ContainerServiceContributor : ServiceViewContributor<ConnectionNode> {

    override fun getViewDescriptor(project: Project): ServiceViewDescriptor =
        object : SimpleServiceViewDescriptor("Apple Container", ContainerIcons.ENGINE) {
            override fun getToolbarActions(): ActionGroup = DefaultActionGroup(
                action("Add Apple Container…", AllIcons.General.Add) { AddConnectionAction.perform(project) },
            )
        }

    override fun getServices(project: Project): List<ConnectionNode> =
        if (ContainerConnectionStore.getInstance(project).isAdded) listOf(ConnectionNode) else emptyList()

    override fun getServiceDescriptor(project: Project, service: ConnectionNode): ServiceViewDescriptor =
        ConnectionDescriptor(project)
}

/** The single connection node; expands into the Containers and Images categories. */
object ConnectionNode : ServiceViewProvidingContributor<CategoryNode, ConnectionNode> {
    override fun asService(): ConnectionNode = this
    override fun getViewDescriptor(project: Project): ServiceViewDescriptor = ConnectionDescriptor(project)
    override fun getServices(project: Project): List<CategoryNode> =
        listOf(CategoryNode(Category.CONTAINERS), CategoryNode(Category.IMAGES))
    override fun getServiceDescriptor(project: Project, service: CategoryNode): ServiceViewDescriptor =
        CategoryDescriptor(project, service.category)
}

enum class Category(val title: String) { CONTAINERS("Containers"), IMAGES("Images") }

/** A grouping node (Containers / Images) whose children are the engine's entities. */
class CategoryNode(val category: Category) : ServiceViewProvidingContributor<EntityNode, CategoryNode> {
    override fun asService(): CategoryNode = this

    override fun getViewDescriptor(project: Project): ServiceViewDescriptor =
        CategoryDescriptor(project, category)

    override fun getServices(project: Project): List<EntityNode> {
        val data = ContainerEngineModel.getInstance(project).snapshot() as? EngineSnapshot.Data ?: return emptyList()
        return when (category) {
            Category.CONTAINERS -> data.containers.map(EntityNode::Container)
            Category.IMAGES -> data.images.map(EntityNode::Image)
        }
    }

    override fun getServiceDescriptor(project: Project, service: EntityNode): ServiceViewDescriptor =
        when (service) {
            is EntityNode.Container -> ContainerDescriptor(project, service.info)
            is EntityNode.Image -> ImageDescriptor(project, service.info)
        }

    override fun equals(other: Any?): Boolean = other is CategoryNode && other.category == category
    override fun hashCode(): Int = category.hashCode()
}

/** A leaf entity: identity is its id/reference so refreshes preserve tree state. */
sealed interface EntityNode {
    class Container(val info: ContainerInfo) : EntityNode {
        override fun equals(other: Any?): Boolean = other is Container && other.info.id == info.id
        override fun hashCode(): Int = info.id.hashCode()
    }

    class Image(val info: ImageInfo) : EntityNode {
        override fun equals(other: Any?): Boolean = other is Image && other.info.reference == info.reference
        override fun hashCode(): Int = info.reference.hashCode()
    }
}

// -- descriptors -----------------------------------------------------------

private class ConnectionDescriptor(private val project: Project) : ServiceViewDescriptor {

    private val model get() = ContainerEngineModel.getInstance(project)

    override fun getPresentation(): ItemPresentation = when (val snapshot = model.snapshot()) {
        is EngineSnapshot.Loading -> data("Apple Container", "loading…", ContainerIcons.ENGINE)
        is EngineSnapshot.NotInstalled ->
            data("Apple Container", "container CLI not found", ContainerIcons.WARNING)
        is EngineSnapshot.Error -> data("Apple Container", snapshot.message, ContainerIcons.WARNING)
        is EngineSnapshot.Data ->
            if (snapshot.running) {
                data(
                    "Apple Container",
                    "running · ${snapshot.containers.size} containers · ${snapshot.images.size} images",
                    ContainerIcons.RUNNING,
                )
            } else {
                data("Apple Container", "stopped", ContainerIcons.STOPPED)
            }
    }

    override fun getContentComponent(): JComponent = DetailPanels.statusPanel(project)

    override fun getToolbarActions(): ActionGroup = DefaultActionGroup(
        action("Start Engine", ContainerIcons.RUNNING) { model.startEngine() },
        action("Stop Engine", ContainerIcons.STOPPED) { model.stopEngine() },
        action("Pull Image…", AllIcons.Actions.Download) { promptPull(project) },
        action("Refresh", AllIcons.Actions.Refresh) { model.refresh() },
    )

    override fun getRemover(): Runnable = Runnable {
        ContainerConnectionStore.getInstance(project).remove()
        model.structureChanged()
    }
}

private class CategoryDescriptor(private val project: Project, private val category: Category) : ServiceViewDescriptor {
    override fun getPresentation(): ItemPresentation {
        val count = (ContainerEngineModel.getInstance(project).snapshot() as? EngineSnapshot.Data)?.let {
            if (category == Category.CONTAINERS) it.containers.size else it.images.size
        }
        val icon = if (category == Category.CONTAINERS) ContainerIcons.CONTAINERS else ContainerIcons.IMAGES
        return data(category.title, count?.let { "$it" } ?: "", icon)
    }
}

private class ContainerDescriptor(private val project: Project, private val info: ContainerInfo) : ServiceViewDescriptor {
    private val model get() = ContainerEngineModel.getInstance(project)

    override fun getPresentation(): ItemPresentation =
        data(info.id, listOfNotNull(info.state, info.ip).joinToString(" · "), ContainerIcons.container(info))

    override fun getContentComponent(): JComponent = DetailPanels.containerPanel(info)

    override fun getToolbarActions(): ActionGroup = DefaultActionGroup(
        action("Start", ContainerIcons.RUNNING) { model.startContainer(info.id) },
        action("Stop", ContainerIcons.STOPPED) { model.stopContainer(info.id) },
        action("Delete", AllIcons.Actions.GC) { model.deleteContainer(info.id) },
        action("Copy IP", AllIcons.Actions.Copy) { info.ip?.let(::copy) },
        action("Copy ID", AllIcons.Actions.Copy) { copy(info.id) },
    )

    override fun getRemover(): Runnable = Runnable { model.deleteContainer(info.id) }
}

private class ImageDescriptor(private val project: Project, private val info: ImageInfo) : ServiceViewDescriptor {
    private val model get() = ContainerEngineModel.getInstance(project)

    override fun getPresentation(): ItemPresentation = data(info.reference, "", ContainerIcons.IMAGE)

    override fun getContentComponent(): JComponent = DetailPanels.imagePanel(info)

    override fun getToolbarActions(): ActionGroup = DefaultActionGroup(
        action("Run…", ContainerIcons.RUNNING) {
            RunContainerDialog(project, info.reference).promptAndRun()
        },
        action("Delete", AllIcons.Actions.GC) { model.deleteImage(info.reference) },
        action("Copy Reference", AllIcons.Actions.Copy) { copy(info.reference) },
    )

    override fun getRemover(): Runnable = Runnable { model.deleteImage(info.reference) }
}

// -- small helpers ---------------------------------------------------------

private fun data(title: String, subtitle: String, icon: javax.swing.Icon): PresentationData =
    PresentationData(title, subtitle.ifEmpty { null }, icon, null)

private fun action(text: String, icon: javax.swing.Icon, run: () -> Unit): DumbAwareAction =
    object : DumbAwareAction(text, null, icon) {
        override fun actionPerformed(e: AnActionEvent) = run()
    }

private fun copy(text: String) = CopyPasteManager.getInstance().setContents(StringSelection(text))

private fun promptPull(project: Project) {
    val ref = com.intellij.openapi.ui.Messages.showInputDialog(
        project, "Image reference (e.g. docker.io/library/alpine:3.22):", "Pull Image", null,
    )?.trim()?.ifEmpty { null } ?: return
    ContainerEngineModel.getInstance(project).pullImage(ref)
}
