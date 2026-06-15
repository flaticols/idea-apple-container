package dev.flaticols.applecontainer.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import dev.flaticols.applecontainer.ContainerIcons

/**
 * Registers "Apple Container" as a run-configuration type (shown in the
 * + New Configuration list, like Docker's), building and running a container
 * from a Dockerfile via the `container` CLI.
 */
class AppleContainerConfigurationType : ConfigurationTypeBase(
    ID,
    "Apple Container",
    "Build and run a container image with Apple Container",
    NotNullLazyValue.createValue { ContainerIcons.ENGINE },
) {

    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun getId(): String = ID
            override fun getOptionsClass(): Class<AppleContainerRunOptions> = AppleContainerRunOptions::class.java
            override fun createTemplateConfiguration(project: Project): RunConfiguration =
                AppleContainerRunConfiguration(project, this, "Apple Container")
        })
    }

    val factory: ConfigurationFactory get() = configurationFactories.first()

    companion object {
        const val ID = "AppleContainerRunConfiguration"
    }
}
