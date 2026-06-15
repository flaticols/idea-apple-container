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
