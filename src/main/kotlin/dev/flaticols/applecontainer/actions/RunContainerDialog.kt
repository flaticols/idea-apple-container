package dev.flaticols.applecontainer.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import dev.flaticols.applecontainer.ContainerEngineModel
import javax.swing.JComponent

/**
 * Minimal "run a container from this image" dialog: optional name and optional
 * command (whitespace-split). The container is started detached so it shows up
 * in the tree. Delegates the actual run to [ContainerEngineModel].
 */
class RunContainerDialog(private val project: Project, private val image: String) : DialogWrapper(project) {

    private val nameField = JBTextField()
    private val commandField = JBTextField()

    init {
        title = "Run $image"
        setOKButtonText("Run")
        init()
    }

    override fun createCenterPanel(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Name:", nameField)
        .addLabeledComponent("Command:", commandField)
        .panel

    override fun getPreferredFocusedComponent(): JComponent = nameField

    fun promptAndRun() {
        if (!showAndGet()) return
        ContainerEngineModel.getInstance(project).runContainer(
            image = image,
            name = nameField.text.trim().ifEmpty { null },
            cmd = commandField.text.trim().split(Regex("\\s+")).filter(String::isNotEmpty),
        )
    }
}
