package dev.flaticols.applecontainer.cli

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.flaticols.applecontainer.model.ContainerInfo
import dev.flaticols.applecontainer.model.ImageInfo
import dev.flaticols.applecontainer.model.MachineInfo
import dev.flaticols.applecontainer.model.NetworkInfo
import dev.flaticols.applecontainer.model.VolumeInfo

/**
 * Parses `container … --format json` output into models. Pure (string in,
 * model out) and defensive: malformed JSON and missing keys yield empty/null
 * rather than throwing, so a CLI shape change degrades gracefully. `internal`
 * so it can be unit-tested against captured fixtures without spawning a process.
 */
internal object ContainerJson {

    fun containers(json: String): List<ContainerInfo> = array(json).mapNotNull(::container)
    fun images(json: String): List<ImageInfo> = array(json).mapNotNull(::image)
    fun machines(json: String): List<MachineInfo> = array(json).mapNotNull(::machine)
    fun volumes(json: String): List<VolumeInfo> = array(json).mapNotNull(::volume)
    fun networks(json: String): List<NetworkInfo> = array(json).mapNotNull(::network)

    /** `status == "running"` from `system status --format json`. */
    fun systemRunning(json: String): Boolean = obj(json).str("status").equals("running", ignoreCase = true)

    /** `apiServerVersion` from `system status --format json`. */
    fun systemVersion(json: String): String? = obj(json).str("apiServerVersion")

    /** Default kernel name (basename of `kernel.binaryPath`) from `system property list`. */
    fun kernelName(json: String): String? = obj(json).obj("kernel").str("binaryPath")?.substringAfterLast('/')

    private fun container(row: JsonObject): ContainerInfo? {
        val config = row.obj("configuration")
        val status = row.obj("status")
        val id = row.str("id") ?: config.str("id") ?: return null
        val platform = config.obj("platform")
        return ContainerInfo(
            id = id,
            image = config.obj("image").str("reference") ?: "",
            state = status.str("state") ?: "unknown",
            ip = status.arr("networks")
                .firstObject()
                .str("ipv4Address")
                ?.substringBefore('/'),  // drop the CIDR suffix
            os = platform.str("os"),
            arch = platform.str("architecture"),
        )
    }

    private fun image(row: JsonObject): ImageInfo? {
        val config = row.obj("configuration")
        val reference = config.str("name") ?: return null
        return ImageInfo(
            reference = reference,
            id = row.str("id") ?: "",
            size = null,  // not exposed by `image list`
        )
    }

    private fun machine(row: JsonObject): MachineInfo? {
        val id = row.str("id") ?: return null
        return MachineInfo(
            id = id,
            state = row.str("status") ?: "unknown",
            ip = row.str("ipAddress"),  // absent while stopped
            cpus = row.int("cpus"),
            memoryBytes = row.long("memory"),
            diskBytes = row.long("diskSize"),
            isDefault = row.bool("default"),
        )
    }

    private fun volume(row: JsonObject): VolumeInfo? {
        val config = row.obj("configuration")
        val id = row.str("id") ?: config.str("name") ?: return null
        return VolumeInfo(
            id = id,
            driver = config.str("driver"),
            format = config.str("format"),
            sizeBytes = config.long("sizeInBytes"),
            source = config.str("source"),
        )
    }

    private fun network(row: JsonObject): NetworkInfo? {
        val config = row.obj("configuration")
        val status = row.obj("status")
        val id = row.str("id") ?: config.str("name") ?: return null
        return NetworkInfo(
            id = id,
            mode = config.str("mode"),
            subnet = status.str("ipv4Subnet"),
            gateway = status.str("ipv4Gateway"),
        )
    }

    private fun obj(json: String): JsonObject =
        runCatching { JsonParser.parseString(json).asJsonObject }.getOrElse { JsonObject() }

    private fun array(json: String): List<JsonObject> =
        runCatching { JsonParser.parseString(json).asJsonArray.mapNotNull { it as? JsonObject } }
            .getOrElse { emptyList() }

    private fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject
    private fun JsonObject?.arr(key: String): JsonArray = (this?.get(key) as? JsonArray) ?: JsonArray()
    private fun JsonObject?.str(key: String): String? =
        this?.get(key)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject?.int(key: String): Int? =
        this?.get(key)?.takeIf { it.isJsonPrimitive }?.runCatching { asInt }?.getOrNull()
    private fun JsonObject?.long(key: String): Long? =
        this?.get(key)?.takeIf { it.isJsonPrimitive }?.runCatching { asLong }?.getOrNull()
    private fun JsonObject?.bool(key: String): Boolean =
        this?.get(key)?.takeIf { it.isJsonPrimitive }?.runCatching { asBoolean }?.getOrNull() ?: false
    private fun JsonArray.firstObject(): JsonObject? = firstOrNull() as? JsonObject
}
