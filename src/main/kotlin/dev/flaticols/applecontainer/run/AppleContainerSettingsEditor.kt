package dev.flaticols.applecontainer.run

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent

/** Editor for an [AppleContainerRunConfiguration]: Dockerfile, context, tag, container name, ports. */
class AppleContainerSettingsEditor : SettingsEditor<AppleContainerRunConfiguration>() {

    private val dockerfile = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            null,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withTitle("Select Dockerfile"),
        )
    }
    private val contextDir = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle("Select Build Context Directory"),
        )
    }
    private val imageTag = JBTextField()
    private val containerName = JBTextField()
    private val ports = JBTextField()
    private val noCache = JBCheckBox("Build without cache (--no-cache)")

    override fun createEditor(): JComponent {
        ports.emptyText.text = "host:container, space-separated (e.g. 8080:8080)"
        imageTag.emptyText.text = "defaults to the context directory name"
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Dockerfile:", dockerfile)
            .addLabeledComponent("Context dir:", contextDir)
            .addLabeledComponent("Image tag:", imageTag)
            .addLabeledComponent("Container name:", containerName)
            .addLabeledComponent("Ports:", ports)
            .addComponent(noCache)
            .panel
    }

    override fun resetEditorFrom(config: AppleContainerRunConfiguration) {
        dockerfile.text = config.dockerfile
        contextDir.text = config.contextDir
        imageTag.text = config.imageTag
        containerName.text = config.containerName
        ports.text = config.ports
        noCache.isSelected = config.noCache
    }

    override fun applyEditorTo(config: AppleContainerRunConfiguration) {
        config.dockerfile = dockerfile.text.trim()
        config.contextDir = contextDir.text.trim()
        config.imageTag = imageTag.text.trim()
        config.containerName = containerName.text.trim()
        config.ports = ports.text.trim()
        config.noCache = noCache.isSelected
    }
}
