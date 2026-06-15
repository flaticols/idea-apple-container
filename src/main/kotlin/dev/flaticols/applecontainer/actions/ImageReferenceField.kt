package dev.flaticols.applecontainer.actions

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.util.Alarm
import dev.flaticols.applecontainer.ContainerEngineModel
import dev.flaticols.applecontainer.cli.DockerHubSearch
import dev.flaticols.applecontainer.model.EngineSnapshot

/**
 * Image-reference input with Docker-style autocompletion: already-pulled images
 * and a curated set of base images are offered offline and instantly, while
 * Docker Hub name-search results stream in (debounced) as the user types.
 */
class ImageReferenceField(project: Project, parent: Disposable) {

    private companion object {
        const val DEBOUNCE_MS = 300

        /** Common base images, offered even before any Hub query. */
        val CURATED = listOf(
            "alpine", "ubuntu", "debian", "busybox", "fedora", "almalinux", "rockylinux",
            "node", "python", "golang", "rust", "eclipse-temurin", "openjdk",
            "nginx", "httpd", "redis", "postgres", "mysql", "mariadb", "mongo",
        )
    }

    /** References of images already on the engine (instant, offline). */
    private val local: List<String> =
        (ContainerEngineModel.getInstance(project).snapshot() as? EngineSnapshot.Data)
            ?.images?.map { it.reference }
            .orEmpty()

    private val provider = TextFieldWithAutoCompletion.StringsCompletionProvider(seed(), null)

    /** The Swing component to drop into a dialog. */
    val component = TextFieldWithAutoCompletion(project, provider, true, "")

    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, parent)

    init {
        component.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) = scheduleHubSearch(component.text.trim())
        })
    }

    val text: String get() = component.text.trim()

    /** Debounced background Hub query; results merge into the offline suggestions. */
    private fun scheduleHubSearch(query: String) {
        alarm.cancelAllRequests()
        if (query.length < DockerHubSearch.MIN_QUERY) return
        alarm.addRequest({
            val hits = DockerHubSearch.search(query)
            ApplicationManager.getApplication().invokeLater { provider.setItems(seed() + hits) }
        }, DEBOUNCE_MS)
    }

    private fun seed(): List<String> = (local + CURATED).distinct()
}
