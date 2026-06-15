package dev.flaticols.applecontainer.cli

import dev.flaticols.applecontainer.cli.Flag.ALL
import dev.flaticols.applecontainer.cli.Flag.CPUS
import dev.flaticols.applecontainer.cli.Flag.DETACH
import dev.flaticols.applecontainer.cli.Flag.ENTRYPOINT
import dev.flaticols.applecontainer.cli.Flag.ENV
import dev.flaticols.applecontainer.cli.Flag.MEMORY
import dev.flaticols.applecontainer.cli.Flag.NAME
import dev.flaticols.applecontainer.cli.Flag.NETWORK
import dev.flaticols.applecontainer.cli.Flag.PUBLISH
import dev.flaticols.applecontainer.cli.Flag.RM
import dev.flaticols.applecontainer.cli.Flag.USER
import dev.flaticols.applecontainer.cli.Flag.VOLUME
import dev.flaticols.applecontainer.cli.Flag.WORKDIR
import dev.flaticols.applecontainer.cli.Flag.RECOMMENDED
import dev.flaticols.applecontainer.cli.Flag.TAR
import dev.flaticols.applecontainer.cli.Flag.BINARY
import dev.flaticols.applecontainer.cli.Flag.ARCH
import dev.flaticols.applecontainer.cli.Flag.FORCE
import dev.flaticols.applecontainer.cli.Flag.SIZE
import dev.flaticols.applecontainer.cli.Flag.SUBNET
import dev.flaticols.applecontainer.cli.Flag.SUBNET_V6
import dev.flaticols.applecontainer.cli.Flag.PLUGIN
import dev.flaticols.applecontainer.cli.Flag.LABEL
import dev.flaticols.applecontainer.cli.Flag.OPT
import dev.flaticols.applecontainer.cli.Flag.OPTION
import dev.flaticols.applecontainer.cli.Flag.INTERNAL
import dev.flaticols.applecontainer.cli.Flag.FOLLOW
import dev.flaticols.applecontainer.cli.Flag.INTERACTIVE
import dev.flaticols.applecontainer.cli.Flag.TTY
import dev.flaticols.applecontainer.cli.Verb.CREATE
import dev.flaticols.applecontainer.cli.Verb.DELETE
import dev.flaticols.applecontainer.cli.Verb.EXEC
import dev.flaticols.applecontainer.cli.Verb.IMAGE
import dev.flaticols.applecontainer.cli.Verb.INSPECT
import dev.flaticols.applecontainer.cli.Verb.KERNEL
import dev.flaticols.applecontainer.cli.Verb.LIST
import dev.flaticols.applecontainer.cli.Verb.LOGS
import dev.flaticols.applecontainer.cli.Verb.LS
import dev.flaticols.applecontainer.cli.Verb.MACHINE
import dev.flaticols.applecontainer.cli.Verb.NETWORK as NETWORK_VERB
import dev.flaticols.applecontainer.cli.Verb.PROPERTY
import dev.flaticols.applecontainer.cli.Verb.PULL
import dev.flaticols.applecontainer.cli.Verb.RUN
import dev.flaticols.applecontainer.cli.Verb.SET
import dev.flaticols.applecontainer.cli.Verb.SET_DEFAULT
import dev.flaticols.applecontainer.cli.Verb.START
import dev.flaticols.applecontainer.cli.Verb.STATUS
import dev.flaticols.applecontainer.cli.Verb.STOP
import dev.flaticols.applecontainer.cli.Verb.SYSTEM
import dev.flaticols.applecontainer.cli.Verb.VOLUME as VOLUME_VERB
import dev.flaticols.applecontainer.model.KernelSpec
import dev.flaticols.applecontainer.model.NetworkSpec
import dev.flaticols.applecontainer.model.RunSpec
import dev.flaticols.applecontainer.model.VolumeSpec

/** Output formats understood by `container ... --format`. */
enum class Format(val wire: String) { JSON("json"), TABLE("table"), YAML("yaml") }

/** Subcommand tokens — the single definition of each verb's wire name. */
enum class Verb(val wire: String) {
    LS("ls"),
    RUN("run"),
    START("start"),
    STOP("stop"),
    DELETE("delete"),
    INSPECT("inspect"),
    CREATE("create"),
    SET_DEFAULT("set-default"),
    LOGS("logs"),
    EXEC("exec"),
    SYSTEM("system"),
    STATUS("status"),
    PROPERTY("property"),
    KERNEL("kernel"),
    SET("set"),
    IMAGE("image"),
    LIST("list"),
    PULL("pull"),
    MACHINE("machine"),
    VOLUME("volume"),
    NETWORK("network"),
}

/** Flag / option names. */
enum class Flag(val wire: String) {
    ALL("--all"),
    DETACH("--detach"),
    NAME("--name"),
    FORMAT("--format"),
    QUIET("--quiet"),
    RM("--rm"),
    ENV("--env"),
    PUBLISH("--publish"),
    VOLUME("--volume"),
    WORKDIR("--workdir"),
    USER("--user"),
    ENTRYPOINT("--entrypoint"),
    NETWORK("--network"),
    CPUS("--cpus"),
    MEMORY("--memory"),
    RECOMMENDED("--recommended"),
    TAR("--tar"),
    BINARY("--binary"),
    ARCH("--arch"),
    FORCE("--force"),
    SIZE("-s"),
    SUBNET("--subnet"),
    SUBNET_V6("--subnet-v6"),
    PLUGIN("--plugin"),
    LABEL("--label"),
    OPT("--opt"),
    OPTION("--option"),
    INTERNAL("--internal"),
    FOLLOW("--follow"),
    INTERACTIVE("--interactive"),
    TTY("--tty"),
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
    fun option(flag: Flag, value: String?) = apply {
        if (!value.isNullOrBlank()) {
            list += flag.wire; list += value
        }
    }

    fun options(flag: Flag, values: Iterable<String>) = apply { values.forEach { option(flag, it) } }
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

    fun run(spec: RunSpec): Command = cmd(RUN) {
        flag(DETACH, spec.detach)
        flag(RM, spec.removeOnExit)
        option(NAME, spec.name)
        option(ENTRYPOINT, spec.entrypoint)
        options(ENV, spec.env)
        options(PUBLISH, spec.ports)
        options(VOLUME, spec.volumes)
        option(WORKDIR, spec.workdir)
        option(USER, spec.user)
        option(NETWORK, spec.network)
        option(CPUS, spec.cpus)
        option(MEMORY, spec.memory)
        arg(spec.image)
        args(spec.command)
    }

    fun start(id: String): Command = cmd(START) { arg(id) }
    fun stop(id: String): Command = cmd(STOP) { arg(id) }
    fun delete(id: String): Command = cmd(DELETE) { arg(id) }
    fun inspect(id: String): Command = cmd(INSPECT) { arg(id) }  // inspect emits JSON natively
    fun logs(id: String, follow: Boolean = false): Command = cmd(LOGS) { flag(FOLLOW, follow); arg(id) }

    /** An interactive command in a running container (a shell, by default). */
    fun exec(id: String, command: List<String> = listOf("sh")): Command =
        cmd(EXEC) { flag(INTERACTIVE); flag(TTY); arg(id); args(command) }

    val system: System = System
    val image: Images = Images
    val machine: Machines = Machines
    val volume: Volumes = Volumes
    val network: Networks = Networks

    object System {
        fun status(format: Format = Format.JSON): Command = cmd(SYSTEM, STATUS) { format(format) }
        fun start(): Command = cmd(SYSTEM, START)
        fun stop(): Command = cmd(SYSTEM, STOP)

        /** System property values — the default kernel lives under `kernel.binaryPath`. */
        fun properties(format: Format = Format.JSON): Command = cmd(SYSTEM, PROPERTY, LIST) { format(format) }

        /** Set the default kernel used by the container runtime. */
        fun setKernel(spec: KernelSpec): Command = cmd(SYSTEM, KERNEL, SET) {
            flag(RECOMMENDED, spec.recommended)
            if (!spec.recommended) {
                option(TAR, spec.tar)
                option(BINARY, spec.binary)
            }
            option(ARCH, spec.arch)
            flag(FORCE, spec.force)
        }
    }

    object Images {
        fun ls(format: Format = Format.JSON): Command = cmd(IMAGE, LIST) { format(format) }
        fun pull(ref: String): Command = cmd(IMAGE, PULL) { arg(ref) }
        fun delete(ref: String): Command = cmd(IMAGE, DELETE) { arg(ref) }
    }

    object Machines {
        fun ls(format: Format = Format.JSON): Command = cmd(MACHINE, LIST) { format(format) }
        fun create(image: String, name: String?): Command =
            cmd(MACHINE, CREATE) { option(NAME, name); arg(image) }

        /** There is no `machine start` verb; booting a stopped machine = run a trivial command. */
        fun start(id: String): Command = cmd(MACHINE, RUN) { option(NAME, id); arg("true") }
        fun stop(id: String): Command = cmd(MACHINE, STOP) { arg(id) }
        fun delete(id: String): Command = cmd(MACHINE, DELETE) { arg(id) }
        fun setDefault(id: String): Command = cmd(MACHINE, SET_DEFAULT) { arg(id) }

        /** Interactive login shell in the machine (`machine run` with no executable). */
        fun shell(id: String): Command = cmd(MACHINE, RUN) { option(NAME, id) }
    }

    object Volumes {
        fun ls(format: Format = Format.JSON): Command = cmd(VOLUME_VERB, LIST) { format(format) }
        fun create(spec: VolumeSpec): Command = cmd(VOLUME_VERB, CREATE) {
            option(SIZE, spec.size)
            options(LABEL, spec.labels)
            options(OPT, spec.opts)
            arg(spec.name)
        }

        fun delete(name: String): Command = cmd(VOLUME_VERB, DELETE) { arg(name) }
    }

    object Networks {
        fun ls(format: Format = Format.JSON): Command = cmd(NETWORK_VERB, LIST) { format(format) }
        fun create(spec: NetworkSpec): Command = cmd(NETWORK_VERB, CREATE) {
            flag(INTERNAL, spec.internal)
            option(SUBNET, spec.subnet)
            option(SUBNET_V6, spec.subnetV6)
            option(PLUGIN, spec.plugin)
            options(LABEL, spec.labels)
            options(OPTION, spec.options)
            arg(spec.name)
        }

        fun delete(name: String): Command = cmd(NETWORK_VERB, DELETE) { arg(name) }
    }
}
