package dev.flaticols.applecontainer.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.util.ui.FormBuilder
import dev.flaticols.applecontainer.ContainerEngineModel
import dev.flaticols.applecontainer.model.RunSpec
import java.awt.Dimension
import javax.swing.JComponent

/**
 * Docker-style "Create Container" form for `container run`: the common fields
 * (name, command, env, ports, volumes, working dir, user, network, resources,
 * --rm). Env/ports/volumes use [ExpandableTextField] — one entry per line when
 * expanded, whitespace-separated when collapsed. Builds a [RunSpec] and hands it
 * to [ContainerEngineModel].
 */
class RunContainerDialog(private val project: Project, private val image: String) : DialogWrapper(project) {

    private val name = JBTextField()
    private val command = JBTextField()
    private val entrypoint = JBTextField()
    private val env = ExpandableTextField()
    private val ports = ExpandableTextField()
    private val volumes = ExpandableTextField()
    private val workdir = JBTextField()
    private val user = JBTextField()
    private val network = JBTextField()
    private val cpus = JBTextField()
    private val memory = JBTextField()
    private val removeOnExit = JBCheckBox("Remove container after it exits (--rm)")

    init {
        title = "Run $image"
        setOKButtonText("Run")
        env.emptyText.text = "KEY=VALUE, space-separated (expand for one per line)"
        ports.emptyText.text = "host:container[/tcp], space-separated"
        volumes.emptyText.text = "host:container, space-separated"
        init()
    }

    override fun createCenterPanel(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Name:", name)
        .addLabeledComponent("Command:", command)
        .addLabeledComponent("Entrypoint:", entrypoint)
        .addLabeledComponent("Environment:", env)
        .addLabeledComponent("Ports:", ports)
        .addLabeledComponent("Volumes:", volumes)
        .addLabeledComponent("Working dir:", workdir)
        .addLabeledComponent("User:", user)
        .addLabeledComponent("Network:", network)
        .addLabeledComponent("CPUs:", cpus)
        .addLabeledComponent("Memory:", memory)
        .addComponent(removeOnExit)
        .panel
        .apply { preferredSize = Dimension(520, preferredSize.height) }

    override fun getPreferredFocusedComponent(): JComponent = name

    fun promptAndRun() {
        if (!showAndGet()) return
        ContainerEngineModel.getInstance(project).runContainer(
            RunSpec(
                image = image,
                name = name.text.trim().ifEmpty { null },
                command = tokens(command.text),
                entrypoint = entrypoint.text.trim().ifEmpty { null },
                env = tokens(env.text),
                ports = tokens(ports.text),
                volumes = tokens(volumes.text),
                workdir = workdir.text.trim().ifEmpty { null },
                user = user.text.trim().ifEmpty { null },
                network = network.text.trim().ifEmpty { null },
                cpus = cpus.text.trim().ifEmpty { null },
                memory = memory.text.trim().ifEmpty { null },
                removeOnExit = removeOnExit.isSelected,
            ),
        )
    }

    /** Split a field on whitespace, dropping blanks. */
    private fun tokens(text: String): List<String> =
        text.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
}
