package dev.flaticols.applecontainer.target

import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import dev.flaticols.applecontainer.ContainerIcons
import javax.swing.Icon

/**
 * Registers "Apple Container" as a Run Target so a run configuration can execute
 * inside an Apple Container machine. Registered via the open
 * `com.intellij.executionTargetType` extension point.
 */
class AppleContainerTargetType :
    TargetEnvironmentType<AppleContainerTargetConfiguration>(AppleContainerTargetConfiguration.TYPE_ID) {

    override val displayName: String get() = "Apple Container"

    override val icon: Icon get() = ContainerIcons.ENGINE

    override fun createDefaultConfig(): AppleContainerTargetConfiguration = AppleContainerTargetConfiguration()

    override fun createSerializer(config: AppleContainerTargetConfiguration): PersistentStateComponent<*> = config

    override fun duplicateConfig(config: AppleContainerTargetConfiguration): AppleContainerTargetConfiguration =
        duplicateTargetConfiguration(this, config)

    override fun createEnvironmentRequest(
        project: Project?,
        config: AppleContainerTargetConfiguration,
    ): TargetEnvironmentRequest = AppleContainerTargetRequest(project, config)

    override fun createConfigurable(
        project: Project,
        config: AppleContainerTargetConfiguration,
        runtimeType: LanguageRuntimeType<*>?,
        parentConfigurable: Configurable?,
    ): Configurable = AppleContainerTargetConfigurable(project, config)
}
