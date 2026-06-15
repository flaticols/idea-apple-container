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
import dev.flaticols.applecontainer.model.KernelSpec
import dev.flaticols.applecontainer.model.NetworkSpec
import dev.flaticols.applecontainer.model.RunSpec
import dev.flaticols.applecontainer.model.VolumeSpec
import dev.flaticols.applecontainer.services.ContainerServiceContributor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

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
    @Volatile
    private var pollJob: Job? = null
    @Volatile
    private var loadedOnce = false
    private val polling = AtomicBoolean(false)

    /**
     * Called by the tree the first time the connection renders (which only
     * happens while the Services panel is visible): kicks off the initial load
     * and starts the poll. Covers the "Services already visible at startup" case
     * where no visibility event fires; [setPollingActive] then owns show/hide.
     */
    fun ensureLoaded() {
        if (loadedOnce) return
        loadedOnce = true
        setPollingActive(true)
    }

    /**
     * Start/stop the background poll. Driven by Services tool-window visibility:
     * while hidden, no `container` commands run; on becoming visible we refresh
     * immediately and resume the gentle poll. The loop is cancelled on project
     * dispose via the service [CoroutineScope].
     */
    fun setPollingActive(active: Boolean) {
        if (active) {
            if (!polling.compareAndSet(false, true)) return
            refresh()  // refresh as soon as the panel appears
            pollJob = cs.launch {
                while (true) {
                    delay(POLL_INTERVAL_MS)
                    refresh()
                }
            }
        } else {
            if (!polling.compareAndSet(true, false)) return
            pollJob?.cancel()
            pollJob = null
        }
    }

    /** Re-poll the engine; the newest request wins if several overlap. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = cs.launch(Dispatchers.IO) {
            val next = fetch()
            // Rebuild the tree only when its *structure* changes (entities
            // added/removed or a running-state flip), not on every volatile
            // field (a running VM's diskSize drifts each poll). Otherwise the
            // reset tears down the selected node and closes its toolbar/popups.
            val restructured = structureKey(state.value) != structureKey(next)
            state.value = next
            if (restructured) structureChanged()
        }
    }

    /** What the tree renders as nodes — excludes volatile per-entity fields. */
    private fun structureKey(snapshot: EngineSnapshot): Any = when (snapshot) {
        is EngineSnapshot.Data -> listOf(
            snapshot.running,
            snapshot.containers.map { it.id to it.state },
            snapshot.images.map { it.reference },
            snapshot.machines.map { it.id to it.state },
            snapshot.volumes.map { it.id },
            snapshot.networks.map { it.id },
        )
        else -> snapshot  // Loading / NotInstalled / Error compare by themselves
    }

    private suspend fun fetch(): EngineSnapshot {
        if (!ContainerCli.isInstalled()) return EngineSnapshot.NotInstalled
        return try {
            val status = ContainerCli.systemStatus()
            if (!status.running) {
                EngineSnapshot.Data(
                    false, status.version, status.kernel,
                    emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
                )
            } else coroutineScope {
                val containers = async { ContainerCli.listContainers() }
                val images = async { ContainerCli.listImages() }
                val machines = async { ContainerCli.listMachines() }
                val volumes = async { ContainerCli.listVolumes() }
                val networks = async { ContainerCli.listNetworks() }
                EngineSnapshot.Data(
                    running = true,
                    version = status.version,
                    kernel = status.kernel,
                    containers = containers.await(),
                    images = images.await(),
                    machines = machines.await(),
                    volumes = volumes.await(),
                    networks = networks.await(),
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
    fun runContainer(spec: RunSpec) =
        command("Running ${spec.image}") { ContainerCli.runContainer(spec) }

    fun startContainer(id: String) = command("Starting $id") { ContainerCli.startContainer(id) }
    fun stopContainer(id: String) = command("Stopping $id") { ContainerCli.stopContainer(id) }
    fun deleteContainer(id: String) = command("Removing $id") { ContainerCli.deleteContainer(id) }

    fun createMachine(image: String, name: String?) =
        command("Creating machine from $image") { ContainerCli.createMachine(image, name) }
    fun startMachine(id: String) = command("Starting machine $id") { ContainerCli.startMachine(id) }
    fun stopMachine(id: String) = command("Stopping machine $id") { ContainerCli.stopMachine(id) }
    fun deleteMachine(id: String) = command("Removing machine $id") { ContainerCli.deleteMachine(id) }
    fun setDefaultMachine(id: String) = command("Setting $id as default machine") { ContainerCli.setDefaultMachine(id) }

    fun createVolume(spec: VolumeSpec) = command("Creating volume ${spec.name}") { ContainerCli.createVolume(spec) }
    fun deleteVolume(name: String) = command("Removing volume $name") { ContainerCli.deleteVolume(name) }
    fun createNetwork(spec: NetworkSpec) = command("Creating network ${spec.name}") { ContainerCli.createNetwork(spec) }
    fun deleteNetwork(name: String) = command("Removing network $name") { ContainerCli.deleteNetwork(name) }
    fun setKernel(spec: KernelSpec) = command("Setting default kernel") { ContainerCli.setKernel(spec) }

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
        private const val POLL_INTERVAL_MS = 5_000L

        fun getInstance(project: Project): ContainerEngineModel = project.service()
    }
}
