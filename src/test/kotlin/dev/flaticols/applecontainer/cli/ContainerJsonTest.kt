package dev.flaticols.applecontainer.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parser tests against the real `container … --format json` shapes captured from
 * container 1.0.0. These pin the shapes (so a CLI key change fails loudly) and
 * the null-safety / CIDR-stripping behaviour.
 */
class ContainerJsonTest {

    @Test
    fun `running container parses with CIDR-stripped ip`() {
        val json = """
            [{"configuration":{"id":"demo","image":{"reference":"docker.io/library/alpine:3.22"},
            "platform":{"architecture":"arm64","os":"linux"}},"id":"demo",
            "status":{"networks":[{"ipv4Address":"192.168.64.2/24"}],"state":"running"}}]
        """.trimIndent()
        val c = ContainerJson.containers(json).single()
        assertEquals("demo", c.id)
        assertEquals("docker.io/library/alpine:3.22", c.image)
        assertEquals("running", c.state)
        assertEquals("192.168.64.2", c.ip)  // CIDR suffix dropped
        assertEquals("linux", c.os)
        assertEquals("arm64", c.arch)
        assertTrue(c.running)
    }

    @Test
    fun `stopped container has no ip`() {
        val json = """
            [{"configuration":{"id":"demo","image":{"reference":"alpine:3.22"},
            "platform":{"architecture":"arm64","os":"linux"}},"id":"demo",
            "status":{"networks":[],"state":"stopped"}}]
        """.trimIndent()
        val c = ContainerJson.containers(json).single()
        assertEquals("stopped", c.state)
        assertNull(c.ip)
        assertTrue(!c.running)
    }

    @Test
    fun `image reference comes from configuration name`() {
        val json = """
            [{"configuration":{"descriptor":{"digest":"sha256:310c","size":9218},
            "name":"docker.io/library/alpine:3.22"},"id":"310c62b5e7ca"}]
        """.trimIndent()
        val img = ContainerJson.images(json).single()
        assertEquals("docker.io/library/alpine:3.22", img.reference)
        assertEquals("310c62b5e7ca", img.id)
    }

    @Test
    fun `running machine parses sizes and default flag`() {
        val json = """
            [{"id":"plugin-builder","status":"running","cpus":4,"ipAddress":"192.168.64.10",
            "memory":8589934592,"diskSize":78839808,"default":true}]
        """.trimIndent()
        val m = ContainerJson.machines(json).single()
        assertEquals("plugin-builder", m.id)
        assertEquals("running", m.state)
        assertEquals("192.168.64.10", m.ip)
        assertEquals(4, m.cpus)
        assertEquals(8_589_934_592L, m.memoryBytes)
        assertEquals(78_839_808L, m.diskBytes)
        assertTrue(m.isDefault)
    }

    @Test
    fun `stopped machine has no ip`() {
        val json = """[{"id":"probe","status":"stopped","cpus":4,"memory":1,"diskSize":2,"default":false}]"""
        val m = ContainerJson.machines(json).single()
        assertEquals("stopped", m.state)
        assertNull(m.ip)
        assertTrue(!m.isDefault)
    }

    @Test
    fun `volume parses driver, format and size`() {
        val json = """
            [{"configuration":{"driver":"local","format":"ext4","name":"probevol",
            "sizeInBytes":67108864,"source":"/Users/den/.../volume.img"},"id":"probevol"}]
        """.trimIndent()
        val v = ContainerJson.volumes(json).single()
        assertEquals("probevol", v.id)
        assertEquals("local", v.driver)
        assertEquals("ext4", v.format)
        assertEquals(67_108_864L, v.sizeBytes)
    }

    @Test
    fun `network subnet and gateway come from status`() {
        val json = """
            [{"configuration":{"mode":"nat","name":"default","plugin":"container-network-vmnet"},
            "id":"default","status":{"ipv4Gateway":"192.168.64.1","ipv4Subnet":"192.168.64.0/24"}}]
        """.trimIndent()
        val n = ContainerJson.networks(json).single()
        assertEquals("default", n.id)
        assertEquals("nat", n.mode)
        assertEquals("192.168.64.0/24", n.subnet)
        assertEquals("192.168.64.1", n.gateway)
    }

    @Test
    fun `system status and version`() {
        val json = """
            {"apiServerVersion":"container-apiserver version 1.0.0 (build: release)","status":"running"}
        """.trimIndent()
        assertTrue(ContainerJson.systemRunning(json))
        assertEquals("container-apiserver version 1.0.0 (build: release)", ContainerJson.systemVersion(json))
    }

    @Test
    fun `kernel name is the binary path basename`() {
        val json = """{"kernel":{"binaryPath":"opt/kata/share/kata-containers/vmlinux-6.18.15-186"}}"""
        assertEquals("vmlinux-6.18.15-186", ContainerJson.kernelName(json))
    }

    @Test
    fun `empty array and malformed json degrade to empty lists`() {
        assertTrue(ContainerJson.containers("[]").isEmpty())
        assertTrue(ContainerJson.machines("not json").isEmpty())
        assertNull(ContainerJson.kernelName("{}"))
    }
}
