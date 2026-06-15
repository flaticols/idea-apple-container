package dev.flaticols.applecontainer.target

import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetPlatform
import com.intellij.execution.target.TargetedCommandLine
import com.intellij.openapi.progress.ProgressIndicator
import dev.flaticols.applecontainer.cli.ContainerCli
import dev.flaticols.applecontainer.cli.ContainerCommands

/**
 * A prepared run target backed by an Apple Container machine. Runs the platform's
 * [TargetedCommandLine] inside the machine via `container machine run … -- argv`.
 * No volume upload: the project is already present at the same path through the
 * virtio-fs home mount, so we only set the working directory and environment.
 */
class AppleContainerTargetEnvironment(
    private val targetRequest: AppleContainerTargetRequest,
    private val machineId: String,
) : TargetEnvironment(targetRequest) {

    override val targetPlatform: TargetPlatform get() = targetRequest.targetPlatform

    override fun createProcess(commandLine: TargetedCommandLine, indicator: ProgressIndicator): Process {
        val argv = commandLine.collectCommandsSynchronously()
        val workdir = commandLine.workingDirectory
        val env = commandLine.environmentVariables.map { (k, v) -> "$k=$v" }
        val command = ContainerCommands.machine.exec(machineId, argv, workdir, env)
        return ContainerCli.commandLine(command).createProcess()
    }

    override fun shutdown() {
        // Machine lifecycle is user-managed; nothing to tear down per run.
    }
}
