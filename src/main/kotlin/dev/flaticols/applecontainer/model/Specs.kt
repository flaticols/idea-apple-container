package dev.flaticols.applecontainer.model

/**
 * Everything the Create Container dialog can specify for `container run`.
 * Lists map to repeated flags; null/blank scalars are omitted.
 */
data class RunSpec(
    val image: String,
    val name: String? = null,
    val command: List<String> = emptyList(),
    val entrypoint: String? = null,
    val env: List<String> = emptyList(),
    val ports: List<String> = emptyList(),
    val volumes: List<String> = emptyList(),
    val workdir: String? = null,
    val user: String? = null,
    val network: String? = null,
    val cpus: String? = null,
    val memory: String? = null,
    val removeOnExit: Boolean = false,
    val detach: Boolean = true,
)

/** All options for `container volume create`. */
data class VolumeSpec(
    val name: String,
    val size: String? = null,
    val labels: List<String> = emptyList(),
    val opts: List<String> = emptyList(),
)

/** All options for `container network create`. */
data class NetworkSpec(
    val name: String,
    val subnet: String? = null,
    val subnetV6: String? = null,
    val plugin: String? = null,
    val internal: Boolean = false,
    val labels: List<String> = emptyList(),
    val options: List<String> = emptyList(),
)

/**
 * What the Set Default Kernel dialog can specify for `container system kernel set`.
 * [recommended] downloads/installs the recommended kernel and takes precedence;
 * otherwise [tar]/[binary] point at a custom kernel.
 */
data class KernelSpec(
    val recommended: Boolean,
    val tar: String? = null,
    val binary: String? = null,
    val arch: String? = null,   // null = CLI default (arm64)
    val force: Boolean = false,
)
