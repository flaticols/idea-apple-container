package dev.flaticols.applecontainer.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import dev.flaticols.applecontainer.ContainerSettings
import dev.flaticols.applecontainer.model.ContainerInfo
import dev.flaticols.applecontainer.model.ImageInfo
import dev.flaticols.applecontainer.model.KernelSpec
import dev.flaticols.applecontainer.model.MachineInfo
import dev.flaticols.applecontainer.model.NetworkInfo
import dev.flaticols.applecontainer.model.NetworkSpec
import dev.flaticols.applecontainer.model.RunSpec
import dev.flaticols.applecontainer.model.SystemStatus
import dev.flaticols.applecontainer.model.VolumeInfo
import dev.flaticols.applecontainer.model.VolumeSpec
import java.io.File

/**
 * The single touchpoint with the `container` CLI. Builds invocations from the
 * typed [ContainerCommands] tree, runs them through IntelliJ's
 * [GeneralCommandLine] (PATH/env/timeout handling), and parses `--format json`
 * output via [ContainerJson].
 *
 * All methods block; callers run them off the EDT (see ContainerEngineModel).
 */
object ContainerCli {

    private const val BINARY = "container"
    private const val DEFAULT_PATH = "/usr/local/bin/container"
    private const val TIMEOUT_MS = 60_000
    private const val LONG_TIMEOUT_MS = 10 * 60_000  // downloads: image pull, kernel install, machine boot

    /** Resolved `container` binary: the configured override if set, else auto-detected. */
    fun binary(): String? {
        ContainerSettings.getInstance().binaryPath.trim().takeIf { it.isNotEmpty() }?.let { return it }
        return detectedBinary()
    }

    /** The `container` binary found on PATH (the `which container` equivalent), or null. */
    fun detectedBinary(): String? {
        PathEnvironmentVariableUtil.findInPath(BINARY)?.let { return it.absolutePath }
        return DEFAULT_PATH.takeIf { File(it).canExecute() }
    }

    /** True when the `container` CLI is available at all. */
    fun isInstalled(): Boolean = binary() != null

    /**
     * Engine status. A non-zero exit (apiserver not running / not registered)
     * is reported as stopped rather than an error — that is a normal state.
     */
    fun systemStatus(): SystemStatus {
        val output = exec(ContainerCommands.system.status())
        if (output.exitCode != 0) return SystemStatus(running = false, version = null, kernel = kernel())
        return SystemStatus(
            running = ContainerJson.systemRunning(output.stdout),
            version = ContainerJson.systemVersion(output.stdout),
            kernel = kernel(),
        )
    }

    /** Default kernel name (basename of its binary path), best-effort. */
    fun kernel(): String? {
        val output = exec(ContainerCommands.system.properties())
        if (output.exitCode != 0) return null
        return ContainerJson.kernelName(output.stdout)
    }

    fun listContainers(): List<ContainerInfo> = ContainerJson.containers(run(ContainerCommands.ls()))
    fun listImages(): List<ImageInfo> = ContainerJson.images(run(ContainerCommands.image.ls()))
    fun listMachines(): List<MachineInfo> = ContainerJson.machines(run(ContainerCommands.machine.ls()))
    fun listVolumes(): List<VolumeInfo> = ContainerJson.volumes(run(ContainerCommands.volume.ls()))
    fun listNetworks(): List<NetworkInfo> = ContainerJson.networks(run(ContainerCommands.network.ls()))

    fun createMachine(image: String, name: String?) {
        run(ContainerCommands.machine.create(image, name?.ifBlank { null }), LONG_TIMEOUT_MS)  // pulls + boots
    }

    fun startMachine(id: String) {
        run(ContainerCommands.machine.start(id), LONG_TIMEOUT_MS)  // boots a VM
    }

    fun stopMachine(id: String) {
        run(ContainerCommands.machine.stop(id))
    }

    fun deleteMachine(id: String) {
        run(ContainerCommands.machine.delete(id))
    }

    fun setDefaultMachine(id: String) {
        run(ContainerCommands.machine.setDefault(id))
    }

    fun startSystem() {
        // Stdin is closed so the (interactive) "install kernel?" prompt cannot hang.
        run(ContainerCommands.system.start(), LONG_TIMEOUT_MS)
    }

    fun stopSystem() {
        run(ContainerCommands.system.stop())
    }

    fun runContainer(spec: RunSpec) {
        run(ContainerCommands.run(spec))
    }

    fun startContainer(id: String) {
        run(ContainerCommands.start(id))
    }

    fun stopContainer(id: String) {
        run(ContainerCommands.stop(id))
    }

    fun deleteContainer(id: String) {
        run(ContainerCommands.delete(id))
    }

    fun pullImage(ref: String) {
        run(ContainerCommands.image.pull(ref), LONG_TIMEOUT_MS)  // downloads
    }

    fun deleteImage(ref: String) {
        run(ContainerCommands.image.delete(ref))
    }

    fun createVolume(spec: VolumeSpec) {
        run(ContainerCommands.volume.create(spec))
    }

    fun deleteVolume(name: String) {
        run(ContainerCommands.volume.delete(name))
    }

    fun createNetwork(spec: NetworkSpec) {
        run(ContainerCommands.network.create(spec))
    }

    fun deleteNetwork(name: String) {
        run(ContainerCommands.network.delete(name))
    }

    fun setKernel(spec: KernelSpec) {
        run(ContainerCommands.system.setKernel(spec), LONG_TIMEOUT_MS)  // may download
    }

    /** A ready-to-run command line for streaming/interactive use (logs, terminal). */
    fun commandLine(command: Command): GeneralCommandLine {
        val exe = binary() ?: throw ContainerCliException("`container` CLI not found on PATH")
        return GeneralCommandLine(exe)
            .withParameters(command.argv)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
    }

    /** Runs a command, returning stdout; throws [ContainerCliException] on failure. */
    private fun run(command: Command, timeoutMs: Int = TIMEOUT_MS): String {
        val output = exec(command, timeoutMs)
        if (output.isTimeout) throw ContainerCliException("`container ${command.argv.joinToString(" ")}` timed out")
        if (output.exitCode != 0) throw ContainerCliException(cliError(output.stderr, output.exitCode))
        return output.stdout
    }

    private fun exec(command: Command, timeoutMs: Int = TIMEOUT_MS): ProcessOutput =
        try {
            CapturingProcessHandler(commandLine(command)).runProcess(timeoutMs)
        } catch (e: ContainerCliException) {
            throw e
        } catch (e: Exception) {
            throw ContainerCliException("cannot run container: ${e.message}")
        }

    private fun cliError(stderr: String, exitCode: Int): String =
        stderr.lineSequence()
            .map(String::trim)
            .firstOrNull { it.isNotEmpty() }
            ?.removePrefix("Error: ")
            ?: "container exited with code $exitCode"
}
