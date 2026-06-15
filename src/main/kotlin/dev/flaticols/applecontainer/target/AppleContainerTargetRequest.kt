package dev.flaticols.applecontainer.target

import com.intellij.execution.ExecutionException
import com.intellij.execution.Platform
import com.intellij.execution.target.BaseTargetEnvironmentRequest
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetPlatform
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.openapi.project.Project

/**
 * Builds the run-target environment for an Apple Container machine. The machine
 * is a Linux VM ([Platform.UNIX]); [prepareEnvironment] validates a machine is
 * selected and hands back the [AppleContainerTargetEnvironment].
 */
class AppleContainerTargetRequest(
    private val project: Project?,
    private val config: AppleContainerTargetConfiguration,
) : BaseTargetEnvironmentRequest() {

    override val configuration: TargetEnvironmentConfiguration get() = config

    override val targetPlatform: TargetPlatform get() = TargetPlatform(Platform.UNIX)

    override fun prepareEnvironment(progressIndicator: TargetProgressIndicator): TargetEnvironment {
        val machineId = config.machineId?.takeIf { it.isNotBlank() }
            ?: throw ExecutionException("No Apple Container machine selected for this run target")
        val environment = AppleContainerTargetEnvironment(this, machineId)
        environmentPrepared(environment, progressIndicator)
        return environment
    }
}
