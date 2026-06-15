package dev.flaticols.applecontainer.target

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.FormBuilder
import dev.flaticols.applecontainer.cli.ContainerCli
import javax.swing.JComponent

/**
 * Target editor: pick the machine to run in. The dropdown is populated from
 * `container machine list`, but stays editable so a name can be typed even when
 * the engine is stopped or the list can't be fetched.
 */
class AppleContainerTargetConfigurable(
    private val project: Project,
    private val config: AppleContainerTargetConfiguration,
) : Configurable {

    private val machineCombo = ComboBox<String>().apply {
        isEditable = true
        runCatching { ContainerCli.listMachines() }.getOrDefault(emptyList())
            .forEach { addItem(it.id) }
        selectedItem = config.machineId.orEmpty()
    }

    override fun getDisplayName(): String = "Apple Container"

    override fun createComponent(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Machine:", machineCombo)
        .panel

    override fun isModified(): Boolean = currentMachine() != config.machineId.orEmpty()

    override fun apply() {
        config.machineId = currentMachine().ifEmpty { null }
    }

    override fun reset() {
        machineCombo.selectedItem = config.machineId.orEmpty()
    }

    private fun currentMachine(): String =
        (machineCombo.editor.item ?: machineCombo.selectedItem)?.toString()?.trim().orEmpty()
}
