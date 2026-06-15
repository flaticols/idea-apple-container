package dev.flaticols.applecontainer

import dev.flaticols.applecontainer.model.EngineSnapshot
import dev.flaticols.applecontainer.model.MachineInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Guards the Services-view reset behaviour: the tree is only rebuilt when the
 * structure key changes, so volatile per-entity fields must NOT affect it (that
 * was the cause of the disappearing toolbar/popups).
 */
class EngineStructureKeyTest {

    private fun data(machines: List<MachineInfo>) = EngineSnapshot.Data(
        running = true,
        version = null,
        kernel = null,
        containers = emptyList(),
        images = emptyList(),
        machines = machines,
        volumes = emptyList(),
        networks = emptyList(),
    )

    private fun machine(state: String = "running", disk: Long? = 100) =
        MachineInfo(id = "m", state = state, ip = null, cpus = 4, memoryBytes = 8, diskBytes = disk, isDefault = true)

    @Test
    fun `volatile diskSize drift does not change the structure key`() {
        val a = data(listOf(machine(disk = 100)))
        val b = data(listOf(machine(disk = 200)))
        assertEquals(engineStructureKey(a), engineStructureKey(b))  // no reset
        assertNotEquals(a, b)  // but the snapshots themselves differ
    }

    @Test
    fun `running-state flip changes the structure key`() {
        assertNotEquals(
            engineStructureKey(data(listOf(machine(state = "running")))),
            engineStructureKey(data(listOf(machine(state = "stopped")))),
        )
    }

    @Test
    fun `adding an entity changes the structure key`() {
        assertNotEquals(
            engineStructureKey(data(emptyList())),
            engineStructureKey(data(listOf(machine()))),
        )
    }
}
