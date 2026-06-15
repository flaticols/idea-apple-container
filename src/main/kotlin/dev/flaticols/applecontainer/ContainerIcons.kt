package dev.flaticols.applecontainer

import com.intellij.icons.AllIcons
import dev.flaticols.applecontainer.model.ContainerInfo
import javax.swing.Icon

/** Icon choices for the Services tree, kept in one place. */
object ContainerIcons {
    val ENGINE: Icon = AllIcons.Nodes.Console
    val CONTAINERS: Icon = AllIcons.Nodes.Folder
    val IMAGES: Icon = AllIcons.Nodes.Folder
    val IMAGE: Icon = AllIcons.FileTypes.Archive
    val RUNNING: Icon = AllIcons.Actions.Execute
    val STOPPED: Icon = AllIcons.Actions.Suspend
    val WARNING: Icon = AllIcons.General.Warning

    fun container(info: ContainerInfo): Icon = if (info.running) RUNNING else STOPPED
}
