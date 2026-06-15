package dev.flaticols.applecontainer.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.ApplicationManager
import dev.flaticols.applecontainer.ContainerSettings
import dev.flaticols.applecontainer.cli.ContainerCli
import dev.flaticols.applecontainer.cli.ContainerCommands
import dev.flaticols.applecontainer.model.RunSpec
import java.io.OutputStream
import java.nio.file.Path
import java.util.UUID

/**
 * Builds the image (`container build`) and then runs it (`container run`),
 * streaming both into one console. The steps run sequentially but
 * asynchronously (driven by process listeners, never blocking the caller): the
 * builder is started best-effort, then the build (which must succeed), then the
 * container runs attached with `--rm` so Stop tears it down.
 */
class AppleContainerRunState(
    private val config: AppleContainerRunConfiguration,
    private val environment: ExecutionEnvironment,
) : RunProfileState {

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val tag = config.imageTag.ifBlank { defaultTag() }
        val containerName = config.containerName.ifBlank { generatedName(tag) }
        val console = TextConsoleBuilderFactory.getInstance().createBuilder(environment.project).console

        val build = ContainerCommands.build(
            tag = tag,
            dockerfile = config.dockerfile.ifBlank { null },
            context = config.contextDir.ifBlank { "." },
            noCache = config.noCache,
        )
        val run = ContainerCommands.run(
            RunSpec(
                image = tag,
                name = containerName,
                ports = config.ports.split(Regex("\\s+")).filter(String::isNotEmpty),
                detach = false,
                removeOnExit = true,
            ),
        )
        val handler = BuildAndRunProcessHandler(
            console,
            containerName,
            listOf(
                Step(ContainerCli.commandLine(ContainerCommands.builderStart()), stopOnFailure = false),
                Step(ContainerCli.commandLine(build), stopOnFailure = true),
                Step(ContainerCli.commandLine(run), stopOnFailure = true),
            ),
        )
        return DefaultExecutionResult(console, handler)
    }

    private fun defaultTag(): String {
        val context = config.contextDir.ifBlank { config.dockerfile.ifBlank { "." } }
        val name = Path.of(context).fileName?.toString()?.lowercase()?.ifBlank { null } ?: "image"
        return "$name:latest"
    }

    /** A unique, valid container name so Stop can target it (the attached client alone can't). */
    private fun generatedName(tag: String): String {
        val base = tag.substringBefore(':').substringAfterLast('/')
            .lowercase().replace(Regex("[^a-z0-9-]"), "-").trim('-').ifEmpty { "container" }
        return "$base-${UUID.randomUUID().toString().take(8)}"
    }
}

private class Step(val commandLine: GeneralCommandLine, val stopOnFailure: Boolean)

/** Runs [steps] sequentially, piping each into [console]; the last one is the live process. */
private class BuildAndRunProcessHandler(
    private val console: ConsoleView,
    private val containerName: String,
    private val steps: List<Step>,
) : ProcessHandler() {

    @Volatile
    private var current: ProcessHandler? = null

    @Volatile
    private var attachedRun = false

    override fun startNotify() {
        super.startNotify()
        runStep(0)
    }

    private fun runStep(index: Int) {
        if (index >= steps.size) {
            notifyProcessTerminated(0)
            return
        }
        val step = steps[index]
        val handler = ColoredProcessHandler(step.commandLine)
        current = handler
        attachedRun = index == steps.lastIndex  // the attached `container run`
        console.attachToProcess(handler)
        handler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                if (event.exitCode != 0 && step.stopOnFailure) {
                    notifyProcessTerminated(event.exitCode)
                } else {
                    runStep(index + 1)
                }
            }
        })
        handler.startNotify()
    }

    /** Stop the container in the VM — killing the attached client alone leaves it running. */
    private fun stopContainer() {
        if (!attachedRun) return
        val stopBuilder = ContainerSettings.getInstance().stopBuilderOnStop
        ApplicationManager.getApplication().executeOnPooledThread {
            runCatching {
                ContainerCli.commandLine(ContainerCommands.stop(containerName)).createProcess().waitFor()
            }
            if (stopBuilder) {
                runCatching {
                    ContainerCli.commandLine(ContainerCommands.builderStop()).createProcess().waitFor()
                }
            }
        }
    }

    override fun destroyProcessImpl() {
        stopContainer()
        current?.destroyProcess() ?: notifyProcessTerminated(0)
    }

    override fun detachProcessImpl() {
        current?.detachProcess() ?: notifyProcessTerminated(0)
    }

    override fun detachIsDefault(): Boolean = false
    override fun getProcessInput(): OutputStream? = null
}
