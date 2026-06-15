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
import com.intellij.openapi.util.text.StringUtil
import com.intellij.navigation.ItemPresentation
import dev.flaticols.applecontainer.ContainerConnectionStore
import dev.flaticols.applecontainer.ContainerEngineModel
import dev.flaticols.applecontainer.ContainerIcons
import dev.flaticols.applecontainer.actions.CreateMachineDialog
import dev.flaticols.applecontainer.actions.CreateNetworkDialog
import dev.flaticols.applecontainer.actions.CreateVolumeDialog
import dev.flaticols.applecontainer.actions.PullImageDialog
import dev.flaticols.applecontainer.actions.SetKernelDialog
import dev.flaticols.applecontainer.actions.addContainerConnection
import dev.flaticols.applecontainer.actions.openInTerminal
import dev.flaticols.applecontainer.actions.RunContainerDialog
import dev.flaticols.applecontainer.cli.ContainerCommands
import dev.flaticols.applecontainer.model.ContainerInfo
import dev.flaticols.applecontainer.model.EngineSnapshot
import dev.flaticols.applecontainer.model.ImageInfo
import dev.flaticols.applecontainer.model.MachineInfo
import dev.flaticols.applecontainer.model.NetworkInfo
import dev.flaticols.applecontainer.model.VolumeInfo
import dev.flaticols.applecontainer.ui.ContainerLogs
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
                action("Add Apple Container…", AllIcons.General.Add) { addContainerConnection(project) },
            )
        }

    override fun getServices(project: Project): List<ConnectionNode> =
        if (ContainerConnectionStore.getInstance(project).isAdded) {
            ContainerEngineModel.getInstance(project).ensureLoaded()  // first render kicks off load + polling
            listOf(ConnectionNode)
        } else {
            emptyList()
        }

    override fun getServiceDescriptor(project: Project, service: ConnectionNode): ServiceViewDescriptor =
        ConnectionDescriptor(project)
}

/** The single connection node; expands into the Containers and Images categories. */
object ConnectionNode : ServiceViewProvidingContributor<CategoryNode, ConnectionNode> {
    override fun asService(): ConnectionNode = this
    override fun getViewDescriptor(project: Project): ServiceViewDescriptor = ConnectionDescriptor(project)
    override fun getServices(project: Project): List<CategoryNode> =
        Category.entries.map(::CategoryNode)
    override fun getServiceDescriptor(project: Project, service: CategoryNode): ServiceViewDescriptor =
        CategoryDescriptor(project, service.category)
}

enum class Category(val title: String) {
    CONTAINERS("Containers"), IMAGES("Images"), MACHINES("Machines"),
    VOLUMES("Volumes"), NETWORKS("Networks"),
}

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
            Category.MACHINES -> data.machines.map(EntityNode::Machine)
            Category.VOLUMES -> data.volumes.map(EntityNode::Volume)
            Category.NETWORKS -> data.networks.map(EntityNode::Network)
        }
    }

    override fun getServiceDescriptor(project: Project, service: EntityNode): ServiceViewDescriptor =
        when (service) {
            is EntityNode.Container -> ContainerDescriptor(project, service.info)
            is EntityNode.Image -> ImageDescriptor(project, service.info)
            is EntityNode.Machine -> MachineDescriptor(project, service.info)
            is EntityNode.Volume -> VolumeDescriptor(project, service.info)
            is EntityNode.Network -> NetworkDescriptor(project, service.info)
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

    class Machine(val info: MachineInfo) : EntityNode {
        override fun equals(other: Any?): Boolean = other is Machine && other.info.id == info.id
        override fun hashCode(): Int = info.id.hashCode()
    }

    class Volume(val info: VolumeInfo) : EntityNode {
        override fun equals(other: Any?): Boolean = other is Volume && other.info.id == info.id
        override fun hashCode(): Int = info.id.hashCode()
    }

    class Network(val info: NetworkInfo) : EntityNode {
        override fun equals(other: Any?): Boolean = other is Network && other.info.id == info.id
        override fun hashCode(): Int = info.id.hashCode()
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
                    ContainerIcons.STATE_RUNNING,
                )
            } else {
                data("Apple Container", "stopped", ContainerIcons.STATE_STOPPED)
            }
    }

    override fun getContentComponent(): JComponent = DetailPanels.statusPanel(project)

    override fun getToolbarActions(): ActionGroup {
        val running = (model.snapshot() as? EngineSnapshot.Data)?.running
        return DefaultActionGroup().apply {
            if (running != true) add(action("Start Engine", ContainerIcons.START) { model.startEngine() })
            if (running != false) add(action("Stop Engine", ContainerIcons.STOP) { model.stopEngine() })
            add(action("Pull Image…", AllIcons.Actions.Download) { promptPull(project) })
            if (running == true) add(action("Set Kernel…", ContainerIcons.KERNEL) { SetKernelDialog(project).promptAndSet() })
            add(action("Refresh", AllIcons.Actions.Refresh) { model.refresh() })
        }
    }

    override fun getRemover(): Runnable = Runnable {
        ContainerConnectionStore.getInstance(project).remove()
        model.structureChanged()
    }
}

private class CategoryDescriptor(private val project: Project, private val category: Category) : ServiceViewDescriptor {
    override fun getPresentation(): ItemPresentation {
        val count = (ContainerEngineModel.getInstance(project).snapshot() as? EngineSnapshot.Data)?.let {
            when (category) {
                Category.CONTAINERS -> it.containers.size
                Category.IMAGES -> it.images.size
                Category.MACHINES -> it.machines.size
                Category.VOLUMES -> it.volumes.size
                Category.NETWORKS -> it.networks.size
            }
        }
        val icon = when (category) {
            Category.CONTAINERS -> ContainerIcons.CONTAINERS
            Category.IMAGES -> ContainerIcons.IMAGES
            Category.MACHINES -> ContainerIcons.MACHINES
            Category.VOLUMES -> ContainerIcons.VOLUMES
            Category.NETWORKS -> ContainerIcons.NETWORKS
        }
        return data(category.title, count?.let { "$it" } ?: "", icon)
    }

    override fun getContentComponent(): JComponent {
        val data = ContainerEngineModel.getInstance(project).snapshot() as? EngineSnapshot.Data
        return when (category) {
            Category.CONTAINERS -> DetailPanels.containersTable(data?.containers.orEmpty())
            Category.IMAGES -> DetailPanels.imagesTable(data?.images.orEmpty())
            Category.MACHINES -> DetailPanels.machinesTable(data?.machines.orEmpty())
            Category.VOLUMES -> DetailPanels.volumesTable(data?.volumes.orEmpty())
            Category.NETWORKS -> DetailPanels.networksTable(data?.networks.orEmpty())
        }
    }

    override fun getToolbarActions(): ActionGroup? = when (category) {
        Category.MACHINES -> DefaultActionGroup(action("Create Machine…", ContainerIcons.CREATE) { promptCreateMachine(project) })
        Category.VOLUMES -> DefaultActionGroup(action("Create Volume…", ContainerIcons.CREATE) { promptCreateVolume(project) })
        Category.NETWORKS -> DefaultActionGroup(action("Create Network…", ContainerIcons.CREATE) { promptCreateNetwork(project) })
        else -> null
    }
}

private class MachineDescriptor(private val project: Project, private val info: MachineInfo) : ServiceViewDescriptor {
    private val model get() = ContainerEngineModel.getInstance(project)

    override fun getPresentation(): ItemPresentation {
        val tags = buildList {
            add(info.state)
            info.ip?.let(::add)
            if (info.isDefault) add("default")
        }
        return data(info.id, tags.joinToString(" · "), ContainerIcons.machine(info))
    }

    override fun getContentComponent(): JComponent = DetailPanels.machinePanel(info)

    override fun getToolbarActions(): ActionGroup = DefaultActionGroup().apply {
        if (info.running) {
            add(action("Stop", ContainerIcons.STOP) { model.stopMachine(info.id) })
            add(action("Open Terminal", ContainerIcons.TERMINAL) { openInTerminal(project, "machine ${info.id}", ContainerCommands.machine.shell(info.id)) })
        } else {
            add(action("Start", ContainerIcons.START) { model.startMachine(info.id) })
        }
        if (!info.isDefault) {
            add(action("Set as Default", AllIcons.Actions.Checked) { model.setDefaultMachine(info.id) })
        }
        add(action("Delete", ContainerIcons.DELETE) { model.deleteMachine(info.id) })
        info.ip?.let { ip -> add(action("Copy IP", ContainerIcons.COPY_IP) { copy(ip) }) }
        add(action("Copy ID", ContainerIcons.COPY_ID) { copy(info.id) })
    }

    override fun getRemover(): Runnable = Runnable { model.deleteMachine(info.id) }
}

private class ContainerDescriptor(private val project: Project, private val info: ContainerInfo) : ServiceViewDescriptor {
    private val model get() = ContainerEngineModel.getInstance(project)

    override fun getPresentation(): ItemPresentation =
        data(info.id, listOfNotNull(info.state, info.ip).joinToString(" · "), ContainerIcons.container(info))

    override fun getContentComponent(): JComponent = DetailPanels.containerPanel(info)

    override fun getToolbarActions(): ActionGroup = DefaultActionGroup().apply {
        if (info.running) {
            add(action("Stop", ContainerIcons.STOP) { model.stopContainer(info.id) })
            add(action("Open Terminal", ContainerIcons.TERMINAL) { openInTerminal(project, "container ${info.id}", ContainerCommands.exec(info.id)) })
        } else {
            add(action("Start", ContainerIcons.START) { model.startContainer(info.id) })
        }
        add(action("Logs", ContainerIcons.LOGS) { ContainerLogs.show(project, info.id, ContainerCommands.logs(info.id, follow = true)) })
        add(action("Delete", ContainerIcons.DELETE) { model.deleteContainer(info.id) })
        info.ip?.let { ip -> add(action("Copy IP", ContainerIcons.COPY_IP) { copy(ip) }) }
        add(action("Copy ID", ContainerIcons.COPY_ID) { copy(info.id) })
    }

    override fun getRemover(): Runnable = Runnable { model.deleteContainer(info.id) }
}

private class ImageDescriptor(private val project: Project, private val info: ImageInfo) : ServiceViewDescriptor {
    private val model get() = ContainerEngineModel.getInstance(project)

    override fun getPresentation(): ItemPresentation = data(info.reference, "", ContainerIcons.IMAGE)

    override fun getContentComponent(): JComponent = DetailPanels.imagePanel(info)

    override fun getToolbarActions(): ActionGroup = DefaultActionGroup(
        action("Run…", ContainerIcons.START) {
            RunContainerDialog(project, info.reference).promptAndRun()
        },
        action("Delete", ContainerIcons.DELETE) { model.deleteImage(info.reference) },
        action("Copy Reference", ContainerIcons.COPY_ID) { copy(info.reference) },
    )

    override fun getRemover(): Runnable = Runnable { model.deleteImage(info.reference) }
}

private class VolumeDescriptor(private val project: Project, private val info: VolumeInfo) : ServiceViewDescriptor {
    private val model get() = ContainerEngineModel.getInstance(project)

    override fun getPresentation(): ItemPresentation {
        val size = info.sizeBytes?.let(StringUtil::formatFileSize)
        return data(info.id, listOfNotNull(info.driver, size).joinToString(" · "), ContainerIcons.VOLUME)
    }

    override fun getContentComponent(): JComponent = DetailPanels.volumePanel(info)

    override fun getToolbarActions(): ActionGroup = DefaultActionGroup(
        action("Delete", ContainerIcons.DELETE) { model.deleteVolume(info.id) },
        action("Copy Name", ContainerIcons.COPY_ID) { copy(info.id) },
    )

    override fun getRemover(): Runnable = Runnable { model.deleteVolume(info.id) }
}

private class NetworkDescriptor(private val project: Project, private val info: NetworkInfo) : ServiceViewDescriptor {
    private val model get() = ContainerEngineModel.getInstance(project)

    override fun getPresentation(): ItemPresentation =
        data(info.id, listOfNotNull(info.mode, info.subnet).joinToString(" · "), ContainerIcons.NETWORK)

    override fun getContentComponent(): JComponent = DetailPanels.networkPanel(info)

    override fun getToolbarActions(): ActionGroup = DefaultActionGroup(
        action("Delete", ContainerIcons.DELETE) { model.deleteNetwork(info.id) },
        action("Copy Name", ContainerIcons.COPY_ID) { copy(info.id) },
    )

    override fun getRemover(): Runnable = Runnable { model.deleteNetwork(info.id) }
}

// -- small helpers ---------------------------------------------------------

private fun data(title: String, subtitle: String, icon: javax.swing.Icon): PresentationData =
    PresentationData(title, subtitle.ifEmpty { null }, icon, null)

private fun action(text: String, icon: javax.swing.Icon, run: () -> Unit): DumbAwareAction =
    object : DumbAwareAction(text, null, icon) {
        override fun actionPerformed(e: AnActionEvent) = run()
    }

private fun copy(text: String) = CopyPasteManager.getInstance().setContents(StringSelection(text))

private fun promptPull(project: Project) = PullImageDialog(project).promptAndPull()

private fun promptCreateMachine(project: Project) = CreateMachineDialog(project).promptAndCreate()

private fun promptCreateVolume(project: Project) = CreateVolumeDialog(project).promptAndCreate()

private fun promptCreateNetwork(project: Project) = CreateNetworkDialog(project).promptAndCreate()
