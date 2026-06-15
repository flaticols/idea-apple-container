package dev.flaticols.applecontainer.cli

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
 * output with the platform-bundled Gson — no extra dependencies.
 *
 * All methods block; callers run them off the EDT (see ContainerEngineModel).
 */
object ContainerCli {

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
        val json = parseObject(output.stdout)
        return SystemStatus(
            running = json.str("status").equals("running", ignoreCase = true),
            version = json.str("apiServerVersion"),
            kernel = kernel(),
        )
    }

    /** Default kernel name (basename of its binary path), best-effort. */
    fun kernel(): String? {
        val output = exec(ContainerCommands.system.properties())
        if (output.exitCode != 0) return null
        return runCatching {
            parseObject(output.stdout).obj("kernel").str("binaryPath")?.substringAfterLast('/')
        }.getOrNull()
    }

    fun listContainers(): List<ContainerInfo> =
        parseArray(run(ContainerCommands.ls())).mapNotNull(::parseContainer)

    fun listImages(): List<ImageInfo> =
        parseArray(run(ContainerCommands.image.ls())).mapNotNull(::parseImage)

    fun listMachines(): List<MachineInfo> =
        parseArray(run(ContainerCommands.machine.ls())).mapNotNull(::parseMachine)

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

    fun listVolumes(): List<VolumeInfo> =
        parseArray(run(ContainerCommands.volume.ls())).mapNotNull(::parseVolume)

    fun createVolume(spec: VolumeSpec) {
        run(ContainerCommands.volume.create(spec))
    }

    fun deleteVolume(name: String) {
        run(ContainerCommands.volume.delete(name))
    }

    fun listNetworks(): List<NetworkInfo> =
        parseArray(run(ContainerCommands.network.ls())).mapNotNull(::parseNetwork)

    fun createNetwork(spec: NetworkSpec) {
        run(ContainerCommands.network.create(spec))
    }

    fun deleteNetwork(name: String) {
        run(ContainerCommands.network.delete(name))
    }

    fun setKernel(spec: KernelSpec) {
        run(ContainerCommands.system.setKernel(spec), LONG_TIMEOUT_MS)  // may download
    }

    // -- execution ---------------------------------------------------------

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

    // -- parsing -----------------------------------------------------------

    private fun parseContainer(row: JsonObject): ContainerInfo? {
        val config = row.obj("configuration")
        val status = row.obj("status")
        val id = row.str("id") ?: config.str("id") ?: return null
        val platform = config.obj("platform")
        return ContainerInfo(
            id = id,
            image = config.obj("image").str("reference") ?: "",
            state = status.str("state") ?: "unknown",
            ip = status.arr("networks")
                .firstObject()
                .str("ipv4Address")
                ?.substringBefore('/'),  // drop the CIDR suffix
            os = platform.str("os"),
            arch = platform.str("architecture"),
        )
    }

    private fun parseImage(row: JsonObject): ImageInfo? {
        val config = row.obj("configuration")
        val reference = config.str("name") ?: return null
        return ImageInfo(
            reference = reference,
            id = row.str("id") ?: "",
            size = null,  // not exposed by `image list`; reserved for a later version
        )
    }

    private fun parseVolume(row: JsonObject): VolumeInfo? {
        val config = row.obj("configuration")
        val id = row.str("id") ?: config.str("name") ?: return null
        return VolumeInfo(
            id = id,
            driver = config.str("driver"),
            format = config.str("format"),
            sizeBytes = config.long("sizeInBytes"),
            source = config.str("source"),
        )
    }

    private fun parseNetwork(row: JsonObject): NetworkInfo? {
        val config = row.obj("configuration")
        val status = row.obj("status")
        val id = row.str("id") ?: config.str("name") ?: return null
        return NetworkInfo(
            id = id,
            mode = config.str("mode"),
            subnet = status.str("ipv4Subnet"),
            gateway = status.str("ipv4Gateway"),
        )
    }

    private fun parseMachine(row: JsonObject): MachineInfo? {
        val id = row.str("id") ?: return null
        return MachineInfo(
            id = id,
            state = row.str("status") ?: "unknown",
            ip = row.str("ipAddress"),  // absent while stopped
            cpus = row.int("cpus"),
            memoryBytes = row.long("memory"),
            diskBytes = row.long("diskSize"),
            isDefault = row.bool("default"),
        )
    }

    private fun parseObject(json: String): JsonObject =
        runCatching { JsonParser.parseString(json).asJsonObject }.getOrElse { JsonObject() }

    private fun parseArray(json: String): List<JsonObject> =
        runCatching { JsonParser.parseString(json).asJsonArray.mapNotNull { it as? JsonObject } }
            .getOrElse { emptyList() }

    // Null-safe Gson accessors (mirrors the reference plugin's CloudDeployApi helpers).
    private fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject
    private fun JsonObject?.arr(key: String): JsonArray = (this?.get(key) as? JsonArray) ?: JsonArray()
    private fun JsonObject?.str(key: String): String? =
        this?.get(key)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject?.int(key: String): Int? =
        this?.get(key)?.takeIf { it.isJsonPrimitive }?.runCatching { asInt }?.getOrNull()
    private fun JsonObject?.long(key: String): Long? =
        this?.get(key)?.takeIf { it.isJsonPrimitive }?.runCatching { asLong }?.getOrNull()
    private fun JsonObject?.bool(key: String): Boolean =
        this?.get(key)?.takeIf { it.isJsonPrimitive }?.runCatching { asBoolean }?.getOrNull() ?: false
    private fun JsonArray.firstObject(): JsonObject? = firstOrNull() as? JsonObject

    private const val BINARY = "container"
    private const val DEFAULT_PATH = "/usr/local/bin/container"
    private const val TIMEOUT_MS = 60_000
    private const val LONG_TIMEOUT_MS = 10 * 60_000  // downloads: image pull, kernel install, machine boot
}
