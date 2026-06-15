package dev.flaticols.applecontainer

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import dev.flaticols.applecontainer.cli.ContainerCli

/**
 * Settings ▸ Tools ▸ Apple Container: an optional override for the `container`
 * binary path. Empty means auto-detect on PATH (the `which container`
 * equivalent), and the detected path is shown as a hint.
 */
class ContainerSettingsConfigurable : BoundConfigurable("Apple Container") {

    private val settings get() = ContainerSettings.getInstance()

    override fun createPanel(): DialogPanel = panel {
        row("Container CLI path:") {
            textFieldWithBrowseButton(
                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                    .withTitle("Select Container Binary"),
            )
                .align(AlignX.FILL)
                .bindText(settings::binaryPath)
        }
        row {
            comment(detectedHint())
        }
        group("Dockerfile Gutter") {
            row {
                checkBox("Include Docker's run options in the gutter menu")
                    .bindSelected(settings::dockerfileGutterCombined)
                    .comment("When off, the gutter menu shows only \"Run on 'Apple Container'\".")
            }
        }
        group("Run / Stop") {
            row {
                checkBox("Stop the build daemon when stopping a run")
                    .bindSelected(settings::stopBuilderOnStop)
                    .comment("Off keeps the buildkit builder warm for fast cached rebuilds.")
            }
        }
    }

    private fun detectedHint(): String =
        ContainerCli.detectedBinary()
            ?.let { "Leave empty to auto-detect on PATH. Detected: <code>$it</code>" }
            ?: "Leave empty to auto-detect on PATH. Not found on PATH — set the full path above."
}
