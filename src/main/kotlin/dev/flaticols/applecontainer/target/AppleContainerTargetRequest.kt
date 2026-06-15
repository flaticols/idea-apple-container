package dev.flaticols.applecontainer.target

import com.intellij.execution.ExecutionException
import com.intellij.execution.Platform
import com.intellij.execution.target.BaseTargetEnvironmentRequest
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetPlatform
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.openapi.project.Project
import java.nio.file.Path

/**
 * Builds the run-target environment for an Apple Container machine (Linux,
 * [Platform.UNIX]). [prepareEnvironment] validates a machine is selected and
 * resolves each upload (e.g. a compiled binary from a Go/Java run config) to a
 * home-mounted staging path so it becomes visible inside the VM.
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

        val staging = Path.of(System.getProperty("user.home"), ".cache", "apple-container", "targets", config.uuid)
        val volumes = uploadVolumes.associateWith { root ->
            val targetPath = when (val target = root.targetRootPath) {
                is TargetEnvironment.TargetPath.Persistent -> target.absolutePath
                else -> staging.resolve(root.localRootPath.fileName?.toString() ?: "upload").toString()
            }
            AppleContainerMountVolume(root.localRootPath, targetPath)
        }

        val environment = AppleContainerTargetEnvironment(this, machineId, volumes)
        environmentPrepared(environment, progressIndicator)
        return environment
    }
}
