package dev.flaticols.applecontainer.target

import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetProgressIndicator
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Makes a local path available inside the machine. There is no `cp`-into-machine
 * in Apple Container — the only shared channel is the virtio-fs home mount — so
 * "upload" is a host-side file copy into a home-mounted directory, which is then
 * instantly visible in the VM at the same path. When the source already lives
 * under the mount (target path == local path), the copy is skipped.
 */
class AppleContainerMountVolume(
    private val local: Path,
    private val target: String,
) : TargetEnvironment.UploadableVolume {

    override val localRoot: Path get() = local
    override val targetRoot: String get() = target

    override fun resolveTargetPath(relativePath: String): String =
        if (relativePath.isEmpty()) target else "$target/$relativePath"

    override fun upload(relativePath: String, targetProgressIndicator: TargetProgressIndicator) {
        val source = local.resolve(relativePath)
        val destination = Path.of(resolveTargetPath(relativePath))
        if (source == destination) return  // already present via the mount
        destination.parent?.let { Files.createDirectories(it) }
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
    }
}
