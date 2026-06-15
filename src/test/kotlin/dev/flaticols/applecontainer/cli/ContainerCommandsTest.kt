package dev.flaticols.applecontainer.cli

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
    fun `run omits name when null and keeps detach plus command`() {
        assertEquals(
            listOf("run", "--detach", "alpine", "sleep", "300"),
            ContainerCommands.run(image = "alpine", command = listOf("sleep", "300")).argv,
        )
    }

    @Test
    fun `run includes name when provided`() {
        assertEquals(
            listOf("run", "--detach", "--name", "demo", "alpine"),
            ContainerCommands.run(image = "alpine", name = "demo").argv,
        )
    }

    @Test
    fun `system properties lists as json`() {
        assertEquals(
            listOf("system", "property", "list", "--format", "json"),
            ContainerCommands.system.properties().argv,
        )
    }
}
