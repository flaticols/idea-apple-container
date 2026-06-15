package dev.flaticols.applecontainer.target

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent

/**
 * Minimal target editor (Phase 1): the machine id to run in. A machine picker /
 * mode toggle lands in Phase 3.
 */
class AppleContainerTargetConfigurable(
    private val project: Project,
    private val config: AppleContainerTargetConfiguration,
) : Configurable {

    private val machineField = JBTextField(config.machineId.orEmpty())

    override fun getDisplayName(): String = "Apple Container"

    override fun createComponent(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Machine:", machineField)
        .panel

    override fun isModified(): Boolean = machineField.text.trim() != config.machineId.orEmpty()

    override fun apply() {
        config.machineId = machineField.text.trim().ifEmpty { null }
    }

    override fun reset() {
        machineField.text = config.machineId.orEmpty()
    }
}
