package dev.flaticols.applecontainer.cli

import kotlinx.serialization.Serializable

@Serializable
internal data class ContainerRow(
    val id: String? = null,
    val configuration: ContainerConfig? = null,
    val status: ContainerStatus? = null,
)
@Serializable
internal data class ContainerConfig(
    val id: String? = null,
    val image: ImageRef? = null,
    val platform: Platform? = null,
)
@Serializable internal data class ImageRef(val reference: String? = null)
@Serializable internal data class Platform(val os: String? = null, val architecture: String? = null)
@Serializable
internal data class ContainerStatus(
    val state: String? = null,
    val networks: List<NetworkAddr> = emptyList(),
)
@Serializable internal data class NetworkAddr(val ipv4Address: String? = null)

@Serializable
internal data class ImageRow(
    val id: String? = null,
    val configuration: ImageConfig? = null,
)
@Serializable internal data class ImageConfig(val name: String? = null)

@Serializable
internal data class MachineRow(
    val id: String? = null,
    val status: String? = null,
    val ipAddress: String? = null,
    val cpus: Int? = null,
    val memory: Long? = null,
    val diskSize: Long? = null,
    val default: Boolean = false,
)

@Serializable
internal data class VolumeRow(
    val id: String? = null,
    val configuration: VolumeConfig? = null,
)
@Serializable
internal data class VolumeConfig(
    val name: String? = null,
    val driver: String? = null,
    val format: String? = null,
    val sizeInBytes: Long? = null,
    val source: String? = null,
)

@Serializable
internal data class NetworkRow(
    val id: String? = null,
    val configuration: NetworkConfig? = null,
    val status: NetworkStatusJson? = null,
)
@Serializable internal data class NetworkConfig(val name: String? = null, val mode: String? = null)
@Serializable internal data class NetworkStatusJson(val ipv4Subnet: String? = null, val ipv4Gateway: String? = null)

@Serializable
internal data class SystemStatusJson(
    val status: String? = null,
    val apiServerVersion: String? = null,
)
@Serializable
internal data class SystemPropertyJson(val kernel: KernelJson? = null)
@Serializable internal data class KernelJson(val binaryPath: String? = null)