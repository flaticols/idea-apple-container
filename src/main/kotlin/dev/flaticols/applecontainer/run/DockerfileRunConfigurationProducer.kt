package dev.flaticols.applecontainer.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement

/**
 * Offers the Apple Container build-and-run config from a Dockerfile /
 * Containerfile — so its editor gutter ▶ popup gains a "Run …" entry that
 * builds and runs the image with `container`, next to Docker's.
 */
class DockerfileRunConfigurationProducer : LazyRunConfigurationProducer<AppleContainerRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory =
        ConfigurationTypeUtil.findConfigurationType(AppleContainerConfigurationType::class.java).factory

    override fun isConfigurationFromContext(
        configuration: AppleContainerRunConfiguration,
        context: ConfigurationContext,
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        return isDockerfile(file.name) && configuration.dockerfile == file.path
    }

    override fun setupConfigurationFromContext(
        configuration: AppleContainerRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        if (!isDockerfile(file.name)) return false
        val dir = file.parent?.name
        configuration.dockerfile = file.path
        configuration.contextDir = file.parent?.path ?: "."
        configuration.imageTag = (dir?.lowercase() ?: "image") + ":latest"
        configuration.name = "Apple Container: ${dir ?: file.name}"
        return true
    }

    private fun isDockerfile(name: String): Boolean =
        name.equals("Dockerfile", ignoreCase = true) ||
            name.equals("Containerfile", ignoreCase = true) ||
            name.endsWith(".Dockerfile", ignoreCase = true) ||
            name.endsWith(".dockerfile", ignoreCase = true)
}
