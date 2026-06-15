package dev.flaticols.applecontainer.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.FormBuilder
import dev.flaticols.applecontainer.ContainerEngineModel
import java.awt.Dimension
import javax.swing.JComponent

/** Pull an image, with Docker-style reference autocompletion (see [ImageReferenceField]). */
class PullImageDialog(private val project: Project) : DialogWrapper(project) {

    private val imageField = ImageReferenceField(project, disposable)

    init {
        title = "Pull Image"
        setOKButtonText("Pull")
        init()
    }

    override fun createCenterPanel(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Image:", imageField.component)
        .panel
        .apply { preferredSize = Dimension(460, preferredSize.height) }

    override fun getPreferredFocusedComponent(): JComponent = imageField.component

    fun promptAndPull() {
        if (!showAndGet()) return
        val ref = imageField.text.ifEmpty { return }
        ContainerEngineModel.getInstance(project).pullImage(ref)
    }
}
