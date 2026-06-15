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
import dev.flaticols.applecontainer.model.MachineInfo
import dev.flaticols.applecontainer.model.SystemStatus
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

    /** Groundwork; not surfaced in the UI yet. */
    fun listMachines(): List<MachineInfo> =
        parseArray(run(ContainerCommands.machine.ls())).mapNotNull(::parseMachine)

    fun startSystem() {
        // Stdin is closed so the (interactive) "install kernel?" prompt cannot hang.
        run(ContainerCommands.system.start())
    }

    fun stopSystem() {
        run(ContainerCommands.system.stop())
    }

    fun runContainer(image: String, name: String?, command: List<String>) {
        run(ContainerCommands.run(image = image, name = name?.ifBlank { null }, command = command))
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
        run(ContainerCommands.image.pull(ref))
    }

    fun deleteImage(ref: String) {
        run(ContainerCommands.image.delete(ref))
    }

    // -- execution ---------------------------------------------------------

    /** Runs a command, returning stdout; throws [ContainerCliException] on failure. */
    private fun run(command: Command): String {
        val output = exec(command)
        if (output.isTimeout) throw ContainerCliException("`container ${command.argv.joinToString(" ")}` timed out")
        if (output.exitCode != 0) throw ContainerCliException(cliError(output.stderr, output.exitCode))
        return output.stdout
    }

    private fun exec(command: Command): ProcessOutput {
        val exe = binary() ?: throw ContainerCliException("`container` CLI not found on PATH")
        val cmd = GeneralCommandLine(exe)
            .withParameters(command.argv)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        return try {
            CapturingProcessHandler(cmd).runProcess(TIMEOUT_MS)
        } catch (e: Exception) {
            throw ContainerCliException("cannot run container: ${e.message}")
        }
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

    private fun parseMachine(row: JsonObject): MachineInfo? {
        val name = row.str("name") ?: return null
        return MachineInfo(name = name, state = row.str("state") ?: "unknown")
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
    private fun JsonArray.firstObject(): JsonObject? = firstOrNull() as? JsonObject

    private const val BINARY = "container"
    private const val DEFAULT_PATH = "/usr/local/bin/container"
    private const val TIMEOUT_MS = 60_000
}
