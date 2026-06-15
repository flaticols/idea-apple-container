package dev.flaticols.applecontainer

import com.intellij.execution.services.ServiceEventListener
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import dev.flaticols.applecontainer.cli.ContainerCli
import dev.flaticols.applecontainer.model.EngineSnapshot
import dev.flaticols.applecontainer.services.ContainerServiceContributor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Cached engine snapshot plus async refresh and command execution. The platform
 * injects a service-scoped [CoroutineScope] (cancelled on project dispose); all
 * CLI work runs on [Dispatchers.IO], and every landed result fires a
 * Services-view reset so the tree re-reads [snapshot].
 */
@Service(Service.Level.PROJECT)
class ContainerEngineModel(
    private val project: Project,
    private val cs: CoroutineScope,
) {

    private val state = MutableStateFlow<EngineSnapshot>(EngineSnapshot.Loading)

    /** Latest snapshot read synchronously by the Services tree. */
    fun snapshot(): EngineSnapshot = state.value

    @Volatile
    private var refreshJob: Job? = null

    /** Re-poll the engine; the newest request wins if several overlap. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = cs.launch(Dispatchers.IO) {
            state.value = fetch()
            structureChanged()
        }
    }

    private suspend fun fetch(): EngineSnapshot {
        if (!ContainerCli.isInstalled()) return EngineSnapshot.NotInstalled
        return try {
            val status = ContainerCli.systemStatus()
            if (!status.running) {
                EngineSnapshot.Data(false, status.version, status.kernel, emptyList(), emptyList())
            } else coroutineScope {
                val containers = async { ContainerCli.listContainers() }
                val images = async { ContainerCli.listImages() }
                EngineSnapshot.Data(
                    running = true,
                    version = status.version,
                    kernel = status.kernel,
                    containers = containers.await(),
                    images = images.await(),
                )
            }
        } catch (e: Exception) {
            EngineSnapshot.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    // -- engine commands ---------------------------------------------------

    fun startEngine() = command("Starting Apple Container engine") { ContainerCli.startSystem() }
    fun stopEngine() = command("Stopping Apple Container engine") { ContainerCli.stopSystem() }
    fun pullImage(ref: String) = command("Pulling $ref") { ContainerCli.pullImage(ref) }
    fun deleteImage(ref: String) = command("Removing image $ref") { ContainerCli.deleteImage(ref) }
    fun runContainer(image: String, name: String?, cmd: List<String>) =
        command("Running $image") { ContainerCli.runContainer(image, name, cmd) }

    fun startContainer(id: String) = command("Starting $id") { ContainerCli.startContainer(id) }
    fun stopContainer(id: String) = command("Stopping $id") { ContainerCli.stopContainer(id) }
    fun deleteContainer(id: String) = command("Removing $id") { ContainerCli.deleteContainer(id) }

    /**
     * Runs a mutating CLI command under a background-progress indicator, reports
     * the outcome as a balloon, then refreshes. Failures surface the CLI's
     * stderr verbatim (e.g. a missing kernel) rather than being swallowed.
     */
    private fun command(title: String, action: () -> Unit) {
        cs.launch {
            val result = withBackgroundProgress(project, title) {
                runCatching { withContext(Dispatchers.IO) { action() } }
            }
            result
                .onSuccess { notify(title, "Done.", NotificationType.INFORMATION) }
                .onFailure { notify("$title failed", it.message ?: "See logs.", NotificationType.ERROR) }
            refresh()
        }
    }

    private fun notify(title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(title, content, type)
            .notify(project)
    }

    /** Asks the Services view to rebuild our contributor's subtree (on the EDT). */
    fun structureChanged() {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            project.messageBus.syncPublisher(ServiceEventListener.TOPIC)
                .handle(ServiceEventListener.ServiceEvent.createResetEvent(ContainerServiceContributor::class.java))
        }
    }

    companion object {
        private const val NOTIFICATION_GROUP = "Apple Container"

        fun getInstance(project: Project): ContainerEngineModel = project.service()
    }
}
