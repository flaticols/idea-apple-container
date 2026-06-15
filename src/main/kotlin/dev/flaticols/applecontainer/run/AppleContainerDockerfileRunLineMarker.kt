package dev.flaticols.applecontainer.run

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import dev.flaticols.applecontainer.ContainerIcons
import dev.flaticols.applecontainer.ContainerSettings

private const val DOCKER_CONTRIBUTOR = "com.intellij.docker.dockerFile.DockerFileRunLineMarkerProvider"

/** The run-marker contributor EP, looked up by name to avoid the internal `EXTENSION` field. */
private val RUN_MARKER_EP =
    ExtensionPointName<LanguageExtensionPoint<RunLineMarkerContributor>>("com.intellij.runLineMarkerContributor")

/**
 * Marks a Dockerfile / Containerfile's first `FROM` with a single run gutter that
 * offers both "Run on 'Apple Container'" and Docker's own run options. The Docker
 * plugin's gutter action is a private self-expanding popup that can't be extended,
 * so this contributor replaces it on that leaf and re-hosts it as one menu entry
 * that delegates back to Docker. Registered only when the Docker plugin is present.
 */
class AppleContainerDockerfileRunLineMarker : RunLineMarkerContributor() {

    override fun getInfo(element: PsiElement): Info? {
        if (element.firstChild != null) return null  // leaves only
        if (!element.text.equals("FROM", ignoreCase = true)) return null
        if (hasEarlierFrom(element)) return null      // mark only the first stage
        val file = element.containingFile?.virtualFile ?: return null
        if (!isDockerfile(file.name)) return null

        val actions = mutableListOf<AnAction>(RunOnAppleContainerAction(file))
        if (ContainerSettings.getInstance().dockerfileGutterCombined) {
            dockerAction(element)?.let { actions += DelegatingRunOnDocker(it) }
        }

        // Replace Docker's marker on this leaf so a single green-arrow gutter shows
        // both our action and Docker's (re-hosted above) in one menu.
        return object : Info(
            AllIcons.RunConfigurations.TestState.Run_run,
            actions.toTypedArray(),
            { "Run on 'Apple Container'" },
        ) {
            override fun shouldReplace(other: Info): Boolean = true
        }
    }

    /** Docker's own gutter action for this `FROM`, if the Docker plugin contributes one. */
    private fun dockerAction(element: PsiElement): AnAction? =
        runCatching {
            RUN_MARKER_EP.extensionList.firstOrNull { it.implementationClass == DOCKER_CONTRIBUTOR }
                ?.instance?.getInfo(element)?.actions?.firstOrNull()
        }.getOrNull()

    private fun hasEarlierFrom(fromKeyword: PsiElement): Boolean {
        var leaf = PsiTreeUtil.prevLeaf(fromKeyword)
        while (leaf != null) {
            if (leaf.text.equals("FROM", ignoreCase = true)) return true
            leaf = PsiTreeUtil.prevLeaf(leaf)
        }
        return false
    }

    private fun isDockerfile(name: String): Boolean =
        name.equals("Dockerfile", ignoreCase = true) ||
            name.equals("Containerfile", ignoreCase = true) ||
            name.endsWith(".Dockerfile", ignoreCase = true) ||
            name.endsWith(".dockerfile", ignoreCase = true)
}

private class RunOnAppleContainerAction(private val file: VirtualFile) :
    AnAction("Run on 'Apple Container'", "Build and run this Dockerfile with Apple Container", ContainerIcons.ENGINE) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dir = file.parent
        val type = ConfigurationTypeUtil.findConfigurationType(AppleContainerConfigurationType::class.java)
        val settings = RunManager.getInstance(project).createConfiguration(
            "Apple Container: ${dir?.name ?: file.name}",
            type.factory,
        )
        (settings.configuration as AppleContainerRunConfiguration).apply {
            dockerfile = file.path
            contextDir = dir?.path ?: "."
            imageTag = (dir?.name?.lowercase() ?: "image") + ":latest"
        }
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }
}

/** Re-hosts Docker's textless gutter action as a labelled "Run on Docker" menu entry. */
private class DelegatingRunOnDocker(private val delegate: AnAction) :
    AnAction("Run on Docker", "Open Docker's run options for this Dockerfile", AllIcons.RunConfigurations.TestState.Run_run) {

    override fun getActionUpdateThread(): ActionUpdateThread = delegate.actionUpdateThread

    override fun actionPerformed(e: AnActionEvent) = delegate.actionPerformed(e)
}
