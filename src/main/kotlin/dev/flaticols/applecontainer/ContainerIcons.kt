package dev.flaticols.applecontainer

import com.intellij.icons.AllIcons
import dev.flaticols.applecontainer.model.ContainerInfo
import dev.flaticols.applecontainer.model.MachineInfo
import javax.swing.Icon

/** Icon choices for the Services tree, kept in one place. */
object ContainerIcons {
    val ENGINE: Icon = AllIcons.Nodes.Console
    val CONTAINERS: Icon = AllIcons.Nodes.Folder
    val IMAGES: Icon = AllIcons.Nodes.Folder
    val MACHINES: Icon = AllIcons.Nodes.Folder
    val VOLUMES: Icon = AllIcons.Nodes.Folder
    val NETWORKS: Icon = AllIcons.Nodes.Folder
    val IMAGE: Icon = AllIcons.FileTypes.Archive
    val VOLUME: Icon = AllIcons.Nodes.DataTables
    val NETWORK: Icon = AllIcons.Nodes.Plugin
    val WARNING: Icon = AllIcons.General.Warning

    // Container actions beyond start/stop.
    val LOGS: Icon = AllIcons.Debugger.Console
    val TERMINAL: Icon = AllIcons.Nodes.Console
    val KERNEL: Icon = AllIcons.Nodes.Plugin
    val CREATE: Icon = AllIcons.General.Add

    // Status glyphs (tree nodes / headers): a clear green "on" vs neutral "off".
    val STATE_RUNNING: Icon = AllIcons.Actions.Execute
    val STATE_STOPPED: Icon = AllIcons.RunConfigurations.TestIgnored

    // Action glyphs (toolbar buttons), distinct from the status glyphs.
    val START: Icon = AllIcons.Actions.Execute
    val STOP: Icon = AllIcons.Actions.Suspend
    val DELETE: Icon = AllIcons.Actions.GC
    val COPY_IP: Icon = AllIcons.General.Web
    val COPY_ID: Icon = AllIcons.Actions.Copy

    private fun state(running: Boolean): Icon = if (running) STATE_RUNNING else STATE_STOPPED
    fun container(info: ContainerInfo): Icon = state(info.running)
    fun machine(info: MachineInfo): Icon = state(info.running)
}
