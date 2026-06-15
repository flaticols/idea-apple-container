package dev.flaticols.applecontainer.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import dev.flaticols.applecontainer.ContainerEngineModel
import dev.flaticols.applecontainer.ContainerIcons
import dev.flaticols.applecontainer.model.ContainerInfo
import dev.flaticols.applecontainer.model.EngineSnapshot
import dev.flaticols.applecontainer.model.ImageInfo
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

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
            header(if (s.running) ContainerIcons.RUNNING else ContainerIcons.STOPPED, "Apple Container",
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

    // -- building blocks ---------------------------------------------------

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
