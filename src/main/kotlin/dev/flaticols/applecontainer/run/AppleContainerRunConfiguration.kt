package dev.flaticols.applecontainer.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

/**
 * "Apple Container" run configuration: build an image from a Dockerfile /
 * Containerfile and run it as a container via the `container` CLI.
 */
class AppleContainerRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    RunConfigurationBase<AppleContainerRunOptions>(project, factory, name) {

    public override fun getOptions(): AppleContainerRunOptions = super.getOptions() as AppleContainerRunOptions

    var dockerfile: String
        get() = options.dockerfile.orEmpty()
        set(value) { options.dockerfile = value }

    var contextDir: String
        get() = options.contextDir.orEmpty()
        set(value) { options.contextDir = value }

    var imageTag: String
        get() = options.imageTag.orEmpty()
        set(value) { options.imageTag = value }

    var containerName: String
        get() = options.containerName.orEmpty()
        set(value) { options.containerName = value }

    var ports: String
        get() = options.ports.orEmpty()
        set(value) { options.ports = value }

    var noCache: Boolean
        get() = options.noCache
        set(value) { options.noCache = value }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfigurationBase<*>> = AppleContainerSettingsEditor()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        AppleContainerRunState(this, environment)
}
