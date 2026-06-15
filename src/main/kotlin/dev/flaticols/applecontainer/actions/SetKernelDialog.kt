package dev.flaticols.applecontainer.actions

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import dev.flaticols.applecontainer.ContainerEngineModel
import dev.flaticols.applecontainer.model.KernelSpec
import java.awt.Dimension
import javax.swing.ButtonGroup
import javax.swing.JComponent

/**
 * Set the default kernel for the container runtime (`container system kernel set`).
 * Default is the one-click recommended download; "Custom" exposes a tar archive
 * (path or URL) plus an in-archive binary member, with arch and force options.
 */
class SetKernelDialog(private val project: Project) : DialogWrapper(project) {

    private val recommended = JBRadioButton("Download and install the recommended kernel", true)
    private val custom = JBRadioButton("Custom kernel")
    private val tar = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withTitle("Select Kernel Tar Archive"),
        )
    }
    private val binary = JBTextField()
    private val arch = ComboBox(arrayOf("arm64", "amd64"))
    private val force = com.intellij.ui.components.JBCheckBox("Overwrite an existing kernel (--force)")

    init {
        title = "Set Default Kernel"
        setOKButtonText("Set Kernel")
        ButtonGroup().apply { add(recommended); add(custom) }
        listOf(recommended, custom).forEach { it.addActionListener { syncEnabled() } }
        syncEnabled()
        init()
    }

    override fun createCenterPanel(): JComponent = FormBuilder.createFormBuilder()
        .addComponent(recommended)
        .addComponent(custom)
        .addLabeledComponent("Tar archive (path or URL):", tar)
        .addLabeledComponent("Binary (archive member):", binary)
        .addLabeledComponent("Architecture:", arch)
        .addComponent(force)
        .panel
        .apply { preferredSize = Dimension(520, preferredSize.height) }

    private fun syncEnabled() {
        val customMode = custom.isSelected
        tar.isEnabled = customMode
        binary.isEnabled = customMode
    }

    override fun doValidate(): ValidationInfo? =
        if (custom.isSelected && tar.text.isBlank() && binary.text.isBlank()) {
            ValidationInfo("Provide a tar archive or a kernel binary", tar)
        } else {
            null
        }

    fun promptAndSet() {
        if (!showAndGet()) return
        ContainerEngineModel.getInstance(project).setKernel(
            KernelSpec(
                recommended = recommended.isSelected,
                tar = tar.text.trim().ifEmpty { null },
                binary = binary.text.trim().ifEmpty { null },
                arch = arch.selectedItem as? String,
                force = force.isSelected,
            ),
        )
    }
}
