package dev.flaticols.applecontainer.cli

import dev.flaticols.applecontainer.model.KernelSpec
import dev.flaticols.applecontainer.model.NetworkSpec
import dev.flaticols.applecontainer.model.RunSpec
import dev.flaticols.applecontainer.model.VolumeSpec
import kotlin.test.Test
import kotlin.test.assertEquals

/** The command tree is pure functions, so it is tested without spawning a process. */
class ContainerCommandsTest {

    @Test
    fun `ls lists all containers as json`() {
        assertEquals(listOf("ls", "--all", "--format", "json"), ContainerCommands.ls().argv)
    }

    @Test
    fun `ls without all drops the flag`() {
        assertEquals(listOf("ls", "--format", "json"), ContainerCommands.ls(all = false).argv)
    }

    @Test
    fun `system status is a json subcommand`() {
        assertEquals(listOf("system", "status", "--format", "json"), ContainerCommands.system.status().argv)
    }

    @Test
    fun `image pull passes the reference positionally`() {
        assertEquals(listOf("image", "pull", "alpine:3.22"), ContainerCommands.image.pull("alpine:3.22").argv)
    }

    @Test
    fun `run omits empty options and keeps detach plus command`() {
        assertEquals(
            listOf("run", "--detach", "alpine", "sleep", "300"),
            ContainerCommands.run(RunSpec(image = "alpine", command = listOf("sleep", "300"))).argv,
        )
    }

    @Test
    fun `run renders the full create-container spec, repeating list flags`() {
        val spec = RunSpec(
            image = "nginx",
            name = "web",
            command = listOf("nginx", "-g", "daemon off;"),
            entrypoint = "/docker-entrypoint.sh",
            env = listOf("TZ=UTC", "DEBUG=1"),
            ports = listOf("8080:80", "8443:443"),
            volumes = listOf("/host:/data"),
            workdir = "/app",
            user = "1000",
            network = "default",
            cpus = "2",
            memory = "512M",
            removeOnExit = true,
        )
        assertEquals(
            listOf(
                "run", "--detach", "--rm", "--name", "web", "--entrypoint", "/docker-entrypoint.sh",
                "--env", "TZ=UTC", "--env", "DEBUG=1",
                "--publish", "8080:80", "--publish", "8443:443",
                "--volume", "/host:/data", "--workdir", "/app", "--user", "1000",
                "--network", "default", "--cpus", "2", "--memory", "512M",
                "nginx", "nginx", "-g", "daemon off;",
            ),
            ContainerCommands.run(spec).argv,
        )
    }

    @Test
    fun `system properties lists as json`() {
        assertEquals(
            listOf("system", "property", "list", "--format", "json"),
            ContainerCommands.system.properties().argv,
        )
    }

    @Test
    fun `machine list as json`() {
        assertEquals(listOf("machine", "list", "--format", "json"), ContainerCommands.machine.ls().argv)
    }

    @Test
    fun `machine create passes image positionally and optional name`() {
        assertEquals(
            listOf("machine", "create", "--name", "dev", "alpine:3.22"),
            ContainerCommands.machine.create("alpine:3.22", "dev").argv,
        )
        assertEquals(
            listOf("machine", "create", "alpine:3.22"),
            ContainerCommands.machine.create("alpine:3.22", null).argv,
        )
    }

    @Test
    fun `machine set-default uses the hyphenated verb`() {
        assertEquals(listOf("machine", "set-default", "dev"), ContainerCommands.machine.setDefault("dev").argv)
    }

    @Test
    fun `machine start boots via run since there is no start verb`() {
        assertEquals(listOf("machine", "run", "--name", "dev", "true"), ContainerCommands.machine.start("dev").argv)
    }

    @Test
    fun `machine shell is run with no executable`() {
        assertEquals(listOf("machine", "run", "--name", "dev"), ContainerCommands.machine.shell("dev").argv)
    }

    @Test
    fun `volume create renders size, repeated labels and opts, then name`() {
        assertEquals(
            listOf("volume", "create", "-s", "512M", "--label", "team=infra", "--opt", "k=v", "data"),
            ContainerCommands.volume.create(
                VolumeSpec(name = "data", size = "512M", labels = listOf("team=infra"), opts = listOf("k=v")),
            ).argv,
        )
        assertEquals(listOf("volume", "create", "data"), ContainerCommands.volume.create(VolumeSpec(name = "data")).argv)
    }

    @Test
    fun `network create renders the full option set`() {
        assertEquals(
            listOf(
                "network", "create", "--internal", "--subnet", "10.0.0.0/24", "--subnet-v6", "fd00::/64",
                "--plugin", "container-network-vmnet", "--label", "env=dev", "--option", "mtu=1400", "net",
            ),
            ContainerCommands.network.create(
                NetworkSpec(
                    name = "net", subnet = "10.0.0.0/24", subnetV6 = "fd00::/64",
                    plugin = "container-network-vmnet", internal = true,
                    labels = listOf("env=dev"), options = listOf("mtu=1400"),
                ),
            ).argv,
        )
    }

    @Test
    fun `kernel set recommended ignores tar and binary`() {
        assertEquals(
            listOf("system", "kernel", "set", "--recommended", "--arch", "arm64"),
            ContainerCommands.system.setKernel(KernelSpec(recommended = true, tar = "x", arch = "arm64")).argv,
        )
    }

    @Test
    fun `kernel set custom uses tar and binary`() {
        assertEquals(
            listOf("system", "kernel", "set", "--tar", "k.tar", "--binary", "vmlinux", "--arch", "amd64", "--force"),
            ContainerCommands.system.setKernel(
                KernelSpec(recommended = false, tar = "k.tar", binary = "vmlinux", arch = "amd64", force = true),
            ).argv,
        )
    }

    @Test
    fun `logs follows and exec opens an interactive tty`() {
        assertEquals(listOf("logs", "--follow", "web"), ContainerCommands.logs("web", follow = true).argv)
        assertEquals(listOf("exec", "--interactive", "--tty", "web", "sh"), ContainerCommands.exec("web").argv)
    }
}
