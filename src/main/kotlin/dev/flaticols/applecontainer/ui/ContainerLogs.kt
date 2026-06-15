package dev.flaticols.applecontainer.ui

import com.intellij.execution.RunContentExecutor
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import dev.flaticols.applecontainer.cli.Command
import dev.flaticols.applecontainer.cli.ContainerCli

/** Streams `container logs -f <id>` into a console in the Run tool window. */
object ContainerLogs {

    fun show(project: Project, id: String, command: Command) {
        val handler = try {
            OSProcessHandler(ContainerCli.commandLine(command))
        } catch (e: Exception) {
            Messages.showErrorDialog(project, e.message ?: "Failed to start logs", "Container Logs")
            return
        }
        RunContentExecutor(project, handler)
            .withTitle("Logs: $id")
            .withActivateToolWindow(true)
            .withStop({ handler.destroyProcess() }, { !handler.isProcessTerminated })
            .run()
    }
}
