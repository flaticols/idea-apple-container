package dev.flaticols.applecontainer.run

import com.intellij.execution.configurations.RunConfigurationOptions

/** Persisted state of an Apple Container build-and-run configuration. */
class AppleContainerRunOptions : RunConfigurationOptions() {
    var dockerfile by string()
    var contextDir by string()
    var imageTag by string()
    var containerName by string()
    var ports by string()       // whitespace-separated host:container[/proto]
    var noCache by property(false)
}
