package dev.flaticols.applecontainer.cli

import dev.flaticols.applecontainer.cli.Flag.ALL
import dev.flaticols.applecontainer.cli.Flag.DETACH
import dev.flaticols.applecontainer.cli.Flag.NAME
import dev.flaticols.applecontainer.cli.Verb.DELETE
import dev.flaticols.applecontainer.cli.Verb.IMAGE
import dev.flaticols.applecontainer.cli.Verb.INSPECT
import dev.flaticols.applecontainer.cli.Verb.LIST
import dev.flaticols.applecontainer.cli.Verb.LS
import dev.flaticols.applecontainer.cli.Verb.MACHINE
import dev.flaticols.applecontainer.cli.Verb.PROPERTY
import dev.flaticols.applecontainer.cli.Verb.PULL
import dev.flaticols.applecontainer.cli.Verb.RUN
import dev.flaticols.applecontainer.cli.Verb.START
import dev.flaticols.applecontainer.cli.Verb.STATUS
import dev.flaticols.applecontainer.cli.Verb.STOP
import dev.flaticols.applecontainer.cli.Verb.SYSTEM

/** Output formats understood by `container ... --format`. */
enum class Format(val wire: String) { JSON("json"), TABLE("table"), YAML("yaml") }

/** Subcommand tokens — the single definition of each verb's wire name. */
enum class Verb(val wire: String) {
    LS("ls"), RUN("run"), START("start"), STOP("stop"), DELETE("delete"), INSPECT("inspect"),
    SYSTEM("system"), STATUS("status"), PROPERTY("property"), IMAGE("image"), LIST("list"),
    PULL("pull"), MACHINE("machine"),
}

/** Flag / option names. */
enum class Flag(val wire: String) {
    ALL("--all"), DETACH("--detach"), NAME("--name"), FORMAT("--format"), QUIET("--quiet"),
}

/** An assembled `container` invocation — the argument vector, without the binary. */
@JvmInline
value class Command(val argv: List<String>)

/**
 * Private arg-builder plumbing. Speaks only enums for structural tokens; the
 * only bare strings it ever appends are genuinely dynamic positionals supplied
 * by the caller (container ids, image refs, user command args).
 */
private class Args {
    val list = mutableListOf<String>()
    fun arg(value: String) = apply { list += value }
    fun args(values: Iterable<String>) = apply { values.forEach(list::add) }
    fun option(flag: Flag, value: String?) = apply { if (value != null) { list += flag.wire; list += value } }
    fun flag(flag: Flag, enabled: Boolean = true) = apply { if (enabled) list += flag.wire }
    fun format(format: Format) = option(Flag.FORMAT, format.wire)
}

private fun cmd(vararg path: Verb, build: Args.() -> Unit = {}): Command =
    Command(path.map(Verb::wire) + Args().apply(build).list)

/**
 * The typed `container` command surface: every verb is a method, every
 * subcommand group is a property. Call sites never spell a verb or flag as a
 * string, and each method is a pure function returning a [Command], so the whole
 * surface is unit-testable without spawning a process.
 */
object ContainerCommands {

    fun ls(all: Boolean = true, format: Format = Format.JSON): Command =
        cmd(LS) { flag(ALL, all); format(format) }

    fun run(
        image: String,
        name: String? = null,
        detach: Boolean = true,
        command: List<String> = emptyList(),
    ): Command = cmd(RUN) {
        flag(DETACH, detach)
        option(NAME, name)
        arg(image)
        args(command)
    }

    fun start(id: String): Command = cmd(START) { arg(id) }
    fun stop(id: String): Command = cmd(STOP) { arg(id) }
    fun delete(id: String): Command = cmd(DELETE) { arg(id) }
    fun inspect(id: String): Command = cmd(INSPECT) { arg(id) }  // inspect emits JSON natively

    val system: System = System
    val image: Images = Images
    val machine: Machines = Machines

    object System {
        fun status(format: Format = Format.JSON): Command = cmd(SYSTEM, STATUS) { format(format) }
        fun start(): Command = cmd(SYSTEM, START)
        fun stop(): Command = cmd(SYSTEM, STOP)

        /** System property values — the default kernel lives under `kernel.binaryPath`. */
        fun properties(format: Format = Format.JSON): Command = cmd(SYSTEM, PROPERTY, LIST) { format(format) }
    }

    object Images {
        fun ls(format: Format = Format.JSON): Command = cmd(IMAGE, LIST) { format(format) }
        fun pull(ref: String): Command = cmd(IMAGE, PULL) { arg(ref) }
        fun delete(ref: String): Command = cmd(IMAGE, DELETE) { arg(ref) }
    }

    /** Groundwork for v0.1; not wired into the UI yet. */
    object Machines {
        fun ls(format: Format = Format.JSON): Command = cmd(MACHINE, LIST) { format(format) }
    }
}
