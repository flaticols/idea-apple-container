package dev.flaticols.applecontainer.model

/** Engine service status from `container system status`. */
data class SystemStatus(
    val running: Boolean,
    val version: String?,
    val kernel: String?,
)

/** One container row from `container ls`. */
data class ContainerInfo(
    val id: String,
    val image: String,
    val state: String,
    val ip: String?,
    val os: String?,
    val arch: String?,
) {
    val running: Boolean get() = state.equals("running", ignoreCase = true)
}

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

/** One image from `container image list`. */
data class ImageInfo(
    val reference: String,
    val id: String,
    val size: Long?,
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

/** One volume from `container volume list`. */
data class VolumeInfo(
    val id: String,
    val driver: String?,
    val format: String?,
    val sizeBytes: Long?,
    val source: String?,
)

/** One network from `container network list`. */
data class NetworkInfo(
    val id: String,
    val mode: String?,
    val subnet: String?,
    val gateway: String?,
)

/** One container machine (a Linux VM) from `container machine list`. */
data class MachineInfo(
    val id: String,
    val state: String,
    val ip: String?,
    val cpus: Int?,
    val memoryBytes: Long?,
    val diskBytes: Long?,
    val isDefault: Boolean,
) {
    val running: Boolean get() = state.equals("running", ignoreCase = true)
}

/**
 * What the Services tree renders for the connection. [Loading] is the initial
 * state; [NotInstalled] means the `container` binary was not found; [Error]
 * carries a CLI failure; [Data] is a successful poll (engine may be stopped, in
 * which case the lists are empty).
 */
sealed interface EngineSnapshot {
    data object Loading : EngineSnapshot
    data object NotInstalled : EngineSnapshot
    data class Error(val message: String) : EngineSnapshot
    data class Data(
        val running: Boolean,
        val version: String?,
        val kernel: String?,
        val containers: List<ContainerInfo>,
        val images: List<ImageInfo>,
        val machines: List<MachineInfo>,
        val volumes: List<VolumeInfo>,
        val networks: List<NetworkInfo>,
    ) : EngineSnapshot
}
