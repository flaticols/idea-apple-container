package dev.flaticols.applecontainer.model

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
