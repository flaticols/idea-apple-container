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

/** One image from `container image list`. */
data class ImageInfo(
    val reference: String,
    val id: String,
    val size: Long?,
)

/** Groundwork for machine support; not surfaced in the UI yet. */
data class MachineInfo(
    val name: String,
    val state: String,
)

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
    ) : EngineSnapshot
}
