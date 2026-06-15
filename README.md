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

## Screenshots

<img width="1864" height="1354" alt="Screenshot 2026-06-15 at 15 35 14" src="https://github.com/user-attachments/assets/b91806b5-ac9c-4ab9-a281-62912bcc260f" />
<img width="313" height="292" alt="Screenshot 2026-06-15 at 11 11 48" src="https://github.com/user-attachments/assets/e978cbb6-743e-4703-bedd-8f3e193c3cd5" />
<img width="1527" height="438" alt="Screenshot 2026-06-15 at 11 11 45" src="https://github.com/user-attachments/assets/e2e347ef-e78c-4347-b859-05b98eaadb4e" />
<img width="1528" height="433" alt="Screenshot 2026-06-15 at 11 11 25" src="https://github.com/user-attachments/assets/d3e0362c-5cd0-4aff-bbc0-cc32de6c07af" />
<img width="649" height="464" alt="Screenshot 2026-06-15 at 11 11 13" src="https://github.com/user-attachments/assets/7a0b584a-342d-46ce-8c50-00e723886c88" />
<img width="739" height="375" alt="Screenshot 2026-06-15 at 19 02 07" src="https://github.com/user-attachments/assets/e473ca9d-14e1-4ac5-952e-50c3b539d43e" />

## Build

```sh
./gradlew test           # unit-test the command tree
./gradlew buildPlugin     # build the distributable zip (build/distributions)
./gradlew runIde          # launch a sandbox IDE with the plugin
```
