package dev.flaticols.applecontainer.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.util.ui.FormBuilder
import dev.flaticols.applecontainer.ContainerEngineModel
import dev.flaticols.applecontainer.model.VolumeSpec
import java.awt.Dimension
import javax.swing.JComponent

/** Create a volume — all `container volume create` options: size, labels, driver opts. */
class CreateVolumeDialog(private val project: Project) : DialogWrapper(project) {

    private val name = JBTextField()
    private val size = JBTextField()
    private val labels = ExpandableTextField()
    private val opts = ExpandableTextField()

    init {
        title = "Create Volume"
        setOKButtonText("Create")
        labels.emptyText.text = "key=value, space-separated"
        opts.emptyText.text = "key=value, space-separated"
        init()
    }

    override fun createCenterPanel(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Name:", name)
        .addLabeledComponent("Size:", size)
        .addLabeledComponent("Labels:", labels)
        .addLabeledComponent("Driver options:", opts)
        .panel
        .apply { preferredSize = Dimension(420, preferredSize.height) }

    override fun getPreferredFocusedComponent(): JComponent = name

    override fun doValidate(): ValidationInfo? =
        if (name.text.isBlank()) ValidationInfo("Enter a volume name", name) else null

    fun promptAndCreate() {
        if (!showAndGet()) return
        ContainerEngineModel.getInstance(project).createVolume(
            VolumeSpec(
                name = name.text.trim(),
                size = size.text.trim().ifEmpty { null },
                labels = tokens(labels.text),
                opts = tokens(opts.text),
            ),
        )
    }

    private fun tokens(text: String): List<String> =
        text.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
}
