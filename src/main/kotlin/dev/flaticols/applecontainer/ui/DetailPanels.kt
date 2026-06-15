package dev.flaticols.applecontainer.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import dev.flaticols.applecontainer.ContainerEngineModel
import dev.flaticols.applecontainer.ContainerIcons
import dev.flaticols.applecontainer.model.ContainerInfo
import dev.flaticols.applecontainer.model.EngineSnapshot
import dev.flaticols.applecontainer.model.ImageInfo
import dev.flaticols.applecontainer.model.MachineInfo
import dev.flaticols.applecontainer.model.NetworkInfo
import dev.flaticols.applecontainer.model.VolumeInfo
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

/**
 * Read-only summaries shown in the Services view's details area. Actions live on
 * the descriptor toolbars, not here.
 */
object DetailPanels {

    fun statusPanel(project: Project): JComponent = when (val s = ContainerEngineModel.getInstance(project).snapshot()) {
        is EngineSnapshot.Loading -> message("Loading…")
        is EngineSnapshot.NotInstalled ->
            message("The `container` CLI was not found. Install Apple Container, then Refresh.")
        is EngineSnapshot.Error -> message(s.message)
        is EngineSnapshot.Data -> page(
            header(if (s.running) ContainerIcons.STATE_RUNNING else ContainerIcons.STATE_STOPPED, "Apple Container",
                if (s.running) "running" else "stopped"),
            form(
                "Status:" to (if (s.running) "running" else "stopped"),
                "Version:" to (s.version ?: "—"),
                "Kernel:" to (s.kernel ?: "—"),
                "Containers:" to s.containers.size.toString(),
                "Images:" to s.images.size.toString(),
            ),
        )
    }

    fun containerPanel(info: ContainerInfo): JComponent = page(
        header(ContainerIcons.container(info), info.id, info.image),
        form(
            "State:" to info.state,
            "IP:" to (info.ip ?: "—"),
            "Image:" to info.image,
            "OS:" to (info.os ?: "—"),
            "Architecture:" to (info.arch ?: "—"),
        ),
    )

    fun imagePanel(info: ImageInfo): JComponent = page(
        header(ContainerIcons.IMAGE, info.reference, ""),
        form(
            "Reference:" to info.reference,
            "ID:" to info.id.take(12).ifEmpty { "—" },
        ),
    )

    fun machinePanel(info: MachineInfo): JComponent = page(
        header(ContainerIcons.machine(info), info.id, info.state),
        form(
            "State:" to info.state,
            "IP:" to (info.ip ?: "—"),
            "CPUs:" to (info.cpus?.toString() ?: "—"),
            "Memory:" to (info.memoryBytes?.let(StringUtil::formatFileSize) ?: "—"),
            "Disk:" to (info.diskBytes?.let(StringUtil::formatFileSize) ?: "—"),
            "Default:" to (if (info.isDefault) "yes" else "no"),
        ),
    )

    fun volumePanel(info: VolumeInfo): JComponent = page(
        header(ContainerIcons.VOLUME, info.id, info.driver ?: ""),
        form(
            "Name:" to info.id,
            "Driver:" to (info.driver ?: "—"),
            "Format:" to (info.format ?: "—"),
            "Size:" to (info.sizeBytes?.let(StringUtil::formatFileSize) ?: "—"),
            "Source:" to (info.source ?: "—"),
        ),
    )

    fun networkPanel(info: NetworkInfo): JComponent = page(
        header(ContainerIcons.NETWORK, info.id, info.mode ?: ""),
        form(
            "Name:" to info.id,
            "Mode:" to (info.mode ?: "—"),
            "Subnet:" to (info.subnet ?: "—"),
            "Gateway:" to (info.gateway ?: "—"),
        ),
    )

    fun containersTable(containers: List<ContainerInfo>): JComponent = table(
        ContainerIcons.CONTAINERS, "Containers", containers,
        columns = arrayOf("ID", "Image", "State", "IP", "OS", "Arch"),
    ) { arrayOf(it.id, it.image, it.state, it.ip ?: "—", it.os ?: "—", it.arch ?: "—") }

    fun imagesTable(images: List<ImageInfo>): JComponent = table(
        ContainerIcons.IMAGES, "Images", images,
        columns = arrayOf("Reference", "ID"),
    ) { arrayOf(it.reference, it.id.take(12)) }

    fun machinesTable(machines: List<MachineInfo>): JComponent = table(
        ContainerIcons.MACHINES, "Machines", machines,
        columns = arrayOf("Name", "State", "IP", "CPUs", "Memory", "Disk", "Default"),
    ) {
        arrayOf(
            it.id, it.state, it.ip ?: "—", it.cpus?.toString() ?: "—",
            it.memoryBytes?.let(StringUtil::formatFileSize) ?: "—",
            it.diskBytes?.let(StringUtil::formatFileSize) ?: "—",
            if (it.isDefault) "yes" else "no",
        )
    }

    fun volumesTable(volumes: List<VolumeInfo>): JComponent = table(
        ContainerIcons.VOLUMES, "Volumes", volumes,
        columns = arrayOf("Name", "Driver", "Format", "Size"),
    ) {
        arrayOf(
            it.id, it.driver ?: "—", it.format ?: "—",
            it.sizeBytes?.let(StringUtil::formatFileSize) ?: "—",
        )
    }

    fun networksTable(networks: List<NetworkInfo>): JComponent = table(
        ContainerIcons.NETWORKS, "Networks", networks,
        columns = arrayOf("Name", "Mode", "Subnet", "Gateway"),
    ) { arrayOf(it.id, it.mode ?: "—", it.subnet ?: "—", it.gateway ?: "—") }

    private fun <T> table(
        icon: Icon,
        title: String,
        rows: List<T>,
        columns: Array<String>,
        cells: (T) -> Array<Any>,
    ): JComponent {
        if (rows.isEmpty()) return message("No ${title.lowercase()} yet.")
        val model = object : DefaultTableModel(rows.map(cells).toTypedArray(), columns) {
            override fun isCellEditable(row: Int, column: Int): Boolean = false
        }
        return JPanel(BorderLayout()).apply {
            add(header(icon, title, "${rows.size}"), BorderLayout.NORTH)
            add(JBScrollPane(JBTable(model).apply { setShowGrid(false) }), BorderLayout.CENTER)
        }
    }

    private fun page(header: JComponent, form: JComponent): JComponent {
        val top = JPanel(BorderLayout()).apply { add(form, BorderLayout.NORTH) }
        return JPanel(BorderLayout()).apply {
            add(header, BorderLayout.NORTH)
            add(top, BorderLayout.CENTER)
        }
    }

    private fun form(vararg rows: Pair<String, String>): JComponent {
        val builder = FormBuilder.createFormBuilder()
        rows.forEach { (label, value) -> builder.addLabeledComponent(label, JBLabel(value)) }
        return builder.panel.apply { border = JBUI.Borders.empty(2, 14, 12, 14) }
    }

    private fun header(icon: Icon?, title: String, subtitle: String): JComponent =
        JBLabel("<html><b>$title</b>&nbsp;&nbsp;<font color='#808080'>$subtitle</font></html>").apply {
            this.icon = icon
            border = JBUI.Borders.empty(10, 12, 6, 12)
        }

    private fun message(text: String): JComponent = JBPanelWithEmptyText().withEmptyText(text)
}
