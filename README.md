# Apple Container — JetBrains plugin

Manage [Apple Container](https://github.com/apple/container) from the IDE's
**Services** tool window: control the container engine and everything it runs —
containers, images, machines, volumes and networks — without leaving your editor.

Requires the `container` CLI (1.0.0+) on macOS, and the bundled Terminal plugin
(for **Open Terminal**).

## Features

**Connection & engine**
- Add **Apple Container** from the Services view's `+` popup (Docker-style).
- Start / stop the engine; status node shows running state, version and kernel.
- **Set Kernel…**: install the recommended kernel, or a custom tar/binary
  (architecture, force overwrite).
- State auto-refreshes in the background — no need to hit Refresh after an action.

**Containers**
- Browse containers; a group view lists them all (id, image, state, IP, …).
- **Run a container** from an image via a Docker-style Create Container form:
  name, command, entrypoint, env, ports, volumes, working dir, user, network,
  CPUs, memory, `--rm`.
- Per-container: start, stop, delete, **Logs** (streamed into a console),
  **Open Terminal** (`exec -it … sh`), Copy IP, Copy ID.

**Images**
- Browse, **Pull** and delete images.
- Image-reference autocompletion (already-pulled images + curated base images +
  live Docker Hub name search) in the Pull and Run/Create dialogs.

**Machines**
- Create (from a base image), start, stop, set-default, delete; **Open Terminal**
  into a running machine. Details show CPUs, memory and disk.

**Volumes & Networks**
- List, create and delete. Create dialogs expose the full CLI option set
  (volume: size, labels, driver options; network: subnet, IPv6 subnet, plugin,
  labels, plugin options, host-only).

**Settings**
- **Settings ▸ Tools ▸ Apple Container** to override the `container` binary path
  (empty = auto-detect on `PATH`, the `which container` equivalent).

## Design

- **Typed command tree** (`cli/ContainerCommands.kt`): every `container`
  subcommand is a method and every flag an enum, so call sites never spell a
  verb or flag as a string and the whole surface is unit-tested without spawning
  a process.
- **`cli/ContainerCli.kt`** runs those commands through IntelliJ's
  `GeneralCommandLine` and parses `--format json` output with the bundled Gson
  (downloads get a longer timeout than quick queries).
- **`ContainerEngineModel`** is a project service with a platform-injected
  `CoroutineScope`: refreshes and commands run on `Dispatchers.IO`, the snapshot
  is held in a `StateFlow`, a background poll keeps it live, and each change
  fires a Services-view reset.
- **`services/ContainerServiceContributor.kt`** builds the tree
  (connection → categories → entities) and hangs actions off the descriptors.

## Build

```sh
./gradlew test           # unit-test the command tree
./gradlew buildPlugin     # build the distributable zip (build/distributions)
./gradlew runIde          # launch a sandbox IDE with the plugin
```
