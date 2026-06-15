# Apple Container — JetBrains plugin

Manage [Apple Container](https://github.com/apple/container) from the IDE's
**Services** tool window: control the container engine and inspect what it runs,
without leaving your editor.

Requires the `container` CLI (1.0.0+) on macOS.

## Features (v0.0.1)

- Add **Apple Container** from the Services view's `+` popup (Docker-style).
- Start / stop the container engine; see its status, version and default kernel.
- Browse **Containers** and **Images**; run a container from an image.
- Per-container actions: start, stop, delete, **Copy IP**, **Copy ID**.
- Pull and delete images.
- **Settings ▸ Tools ▸ Apple Container** to override the `container` binary path
  (empty = auto-detect on PATH, the `which container` equivalent).

Machine management is groundworked but not yet surfaced — it lands in a later
version.

## Design

- **Typed command tree** (`cli/ContainerCommands.kt`): every `container`
  subcommand is a method and every flag an enum, so call sites never spell a
  verb or flag as a string and the whole surface is unit-tested without spawning
  a process.
- **`cli/ContainerCli.kt`** runs those commands through IntelliJ's
  `GeneralCommandLine` and parses `--format json` output with the bundled Gson.
- **`ContainerEngineModel`** is a project service with a platform-injected
  `CoroutineScope`: refreshes and commands run on `Dispatchers.IO`, the snapshot
  is held in a `StateFlow`, and each result fires a Services-view reset.
- **`services/ContainerServiceContributor.kt`** builds the tree
  (connection → categories → entities) and hangs actions off the descriptors.

## Build

```sh
./gradlew test          # unit-test the command tree
./gradlew buildPlugin    # build the distributable zip (build/distributions)
./gradlew runIde         # launch a sandbox IDE with the plugin
```
