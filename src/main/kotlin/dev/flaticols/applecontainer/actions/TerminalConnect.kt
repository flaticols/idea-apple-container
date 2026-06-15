package dev.flaticols.applecontainer.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import dev.flaticols.applecontainer.cli.Command
import dev.flaticols.applecontainer.cli.ContainerCli
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

/**
 * Opens a tab in the IDE Terminal tool window and runs [command] there — used to
 * attach to a container (`exec -it … sh`) or a machine (`machine run`). The
 * Terminal provides the interactive TTY these commands need.
 *
 * Note: [TerminalToolWindowManager.createShellWidget] is deprecated but has no
 * public, non-internal replacement yet (the new-terminal entry points are
 * `@ApiStatus.Internal`, which the plugin verifier rejects). It is not scheduled
 * for removal, so it is the safest available option.
 */
fun openInTerminal(project: Project, label: String, command: Command) {
    val exe = ContainerCli.binary()
    if (exe == null) {
        Messages.showErrorDialog(project, "The `container` CLI was not found.", "Open Terminal")
        return
    }
    val line = (listOf(exe) + command.argv).joinToString(" ", transform = ::shellQuote)
    @Suppress("DEPRECATION")
    val widget = TerminalToolWindowManager.getInstance(project)
        .createShellWidget(project.basePath, "container: $label", true, true)
    widget.sendCommandToExecute(line)
}

/** Minimal POSIX single-quote escaping for the assembled command line. */
private fun shellQuote(arg: String): String =
    if (arg.isNotEmpty() && arg.all { it.isLetterOrDigit() || it in "_-./:=" }) {
        arg
    } else {
        "'" + arg.replace("'", "'\\''") + "'"
    }
