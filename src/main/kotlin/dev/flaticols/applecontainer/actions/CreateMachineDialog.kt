package dev.flaticols.applecontainer.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import dev.flaticols.applecontainer.ContainerEngineModel
import java.awt.Dimension
import javax.swing.JComponent

/**
 * Create a container machine from a base image, with Docker-style reference
 * autocompletion (see [ImageReferenceField]) and an optional machine name.
 */
class CreateMachineDialog(private val project: Project) : DialogWrapper(project) {

    private val imageField = ImageReferenceField(project, disposable)
    private val nameField = JBTextField()

    init {
        title = "Create Machine"
        setOKButtonText("Create")
        init()
    }

    override fun createCenterPanel(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Base image:", imageField.component)
        .addLabeledComponent("Name:", nameField)
        .panel
        .apply { preferredSize = Dimension(460, preferredSize.height) }

    override fun getPreferredFocusedComponent(): JComponent = imageField.component

    fun promptAndCreate() {
        if (!showAndGet()) return
        val image = imageField.text.ifEmpty { return }
        ContainerEngineModel.getInstance(project).createMachine(image, nameField.text.trim().ifEmpty { null })
    }
}
