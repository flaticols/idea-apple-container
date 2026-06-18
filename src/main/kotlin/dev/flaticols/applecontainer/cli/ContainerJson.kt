package dev.flaticols.applecontainer.cli

import dev.flaticols.applecontainer.model.ContainerInfo
import dev.flaticols.applecontainer.model.ImageInfo
import dev.flaticols.applecontainer.model.MachineInfo
import dev.flaticols.applecontainer.model.NetworkInfo
import dev.flaticols.applecontainer.model.VolumeInfo
import kotlinx.serialization.json.Json

internal object ContainerJson {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun containers(text: String): List<ContainerInfo> =
        decodeList<ContainerRow>(text).mapNotNull { row ->
            val id = row.id ?: row.configuration?.id ?: return@mapNotNull null
            ContainerInfo(
                id = id,
                image = row.configuration?.image?.reference ?: "",
                state = row.status?.state ?: "unknown",
                ip = row.status?.networks?.firstOrNull()?.ipv4Address?.substringBefore('/'),
                os = row.configuration?.platform?.os,
                arch = row.configuration?.platform?.architecture,
            )
        }

    fun images(text: String): List<ImageInfo> =
        decodeList<ImageRow>(text).mapNotNull { row ->
            val reference = row.configuration?.name ?: return@mapNotNull null
            ImageInfo(reference = reference, id = row.id ?: "", size = null)
        }

    fun machines(text: String): List<MachineInfo> =
        decodeList<MachineRow>(text).mapNotNull { row ->
            val id = row.id ?: return@mapNotNull null
            MachineInfo(
                id = id,
                state = row.status ?: "unknown",
                ip = row.ipAddress,
                cpus = row.cpus,
                memoryBytes = row.memory,
                diskBytes = row.diskSize,
                isDefault = row.default,
            )
        }

    fun volumes(text: String): List<VolumeInfo> =
        decodeList<VolumeRow>(text).mapNotNull { row ->
            val id = row.id ?: row.configuration?.name ?: return@mapNotNull null
            VolumeInfo(
                id = id,
                driver = row.configuration?.driver,
                format = row.configuration?.format,
                sizeBytes = row.configuration?.sizeInBytes,
                source = row.configuration?.source,
            )
        }

    fun networks(text: String): List<NetworkInfo> =
        decodeList<NetworkRow>(text).mapNotNull { row ->
            val id = row.id ?: row.configuration?.name ?: return@mapNotNull null
            NetworkInfo(
                id = id,
                mode = row.configuration?.mode,
                subnet = row.status?.ipv4Subnet,
                gateway = row.status?.ipv4Gateway,
            )
        }

    fun systemRunning(text: String): Boolean =
        decode<SystemStatusJson>(text)?.status.equals("running", ignoreCase = true)

    fun systemVersion(text: String): String? =
        decode<SystemStatusJson>(text)?.apiServerVersion

    fun kernelName(text: String): String? =
        decode<SystemPropertyJson>(text)?.kernel?.binaryPath?.substringAfterLast('/')

    private inline fun <reified T> decodeList(text: String): List<T> =
        runCatching { json.decodeFromString<List<T>>(text) }.getOrElse { emptyList() }

    private inline fun <reified T> decode(text: String): T? =
        runCatching { json.decodeFromString<T>(text) }.getOrNull()
}