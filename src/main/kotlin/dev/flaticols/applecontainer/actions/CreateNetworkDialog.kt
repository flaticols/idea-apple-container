package dev.flaticols.applecontainer.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.util.ui.FormBuilder
import dev.flaticols.applecontainer.ContainerEngineModel
import dev.flaticols.applecontainer.model.NetworkSpec
import java.awt.Dimension
import javax.swing.JComponent

/** Create a network — all `container network create` options. */
class CreateNetworkDialog(private val project: Project) : DialogWrapper(project) {

    private val name = JBTextField()
    private val subnet = JBTextField()
    private val subnetV6 = JBTextField()
    private val plugin = JBTextField()
    private val internal = JBCheckBox("Host-only (--internal)")
    private val labels = ExpandableTextField()
    private val options = ExpandableTextField()

    init {
        title = "Create Network"
        setOKButtonText("Create")
        plugin.emptyText.text = "container-network-vmnet"
        labels.emptyText.text = "key=value, space-separated"
        options.emptyText.text = "key=value, space-separated"
        init()
    }

    override fun createCenterPanel(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Name:", name)
        .addLabeledComponent("Subnet:", subnet)
        .addLabeledComponent("Subnet (IPv6):", subnetV6)
        .addLabeledComponent("Plugin:", plugin)
        .addLabeledComponent("Labels:", labels)
        .addLabeledComponent("Plugin options:", options)
        .addComponent(internal)
        .panel
        .apply { preferredSize = Dimension(420, preferredSize.height) }

    override fun getPreferredFocusedComponent(): JComponent = name

    override fun doValidate(): ValidationInfo? =
        if (name.text.isBlank()) ValidationInfo("Enter a network name", name) else null

    fun promptAndCreate() {
        if (!showAndGet()) return
        ContainerEngineModel.getInstance(project).createNetwork(
            NetworkSpec(
                name = name.text.trim(),
                subnet = subnet.text.trim().ifEmpty { null },
                subnetV6 = subnetV6.text.trim().ifEmpty { null },
                plugin = plugin.text.trim().ifEmpty { null },
                internal = internal.isSelected,
                labels = tokens(labels.text),
                options = tokens(options.text),
            ),
        )
    }

    private fun tokens(text: String): List<String> =
        text.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
}
