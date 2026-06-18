import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "dev.flaticols.applecontainer"
version = "0.0.12"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

// Prefer a locally installed IDE (no multi-GB download). The plugin uses only
// platform APIs, so any IntelliJ-based IDE works as the compile target.
val localIde = sequenceOf(
    "/Applications/IntelliJ IDEA.app",
    "/Applications/IntelliJ IDEA Ultimate.app",
    "/Applications/IntelliJ IDEA CE.app",
    "/Applications/GoLand.app",
).map(::file).firstOrNull { it.exists() }

dependencies {
    intellijPlatform {
        if (localIde != null) {
            local(localIde)
        } else {
            intellijIdea("2026.1.1")
        }
        bundledPlugin("org.jetbrains.plugins.terminal")  // "Open Terminal" attaches via the Terminal tool window
        bundledPlugin("Docker")  // Dockerfile language + run-marker, for the gutter coexistence test
        pluginVerifier()
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
        }
        changeNotes = """
            <h4>0.0.11</h4>
            <ul>
              <li><b>Stop</b> on an Apple Container run configuration now actually stops the container (<code>container stop</code>), instead of only detaching the attached client and leaving it running.</li>
              <li>Optional setting (<b>Tools ▸ Apple Container</b>): also stop the buildkit build daemon on Stop. Off by default so the builder stays warm for cached rebuilds.</li>
            </ul>
            <h4>0.0.10</h4>
            <ul>
              <li>The Dockerfile <b>FROM</b> gutter shows a single run icon whose menu offers both <b>Run on 'Apple Container'</b> and <b>Run on Docker</b> (Docker's own options). A new setting (<b>Tools ▸ Apple Container</b>) can switch it to show only the Apple Container action.</li>
            </ul>
            <h4>0.0.7</h4>
            <ul>
              <li><b>Run/Debug inside a machine</b>: an Apple Container <b>run target</b> executes any run configuration in a machine, using the home mount so no file upload is needed.</li>
              <li><b>Build &amp; run a Dockerfile</b>: a new "Apple Container" run configuration builds an image and runs the container; available from <b>+ New Configuration</b> and from the Dockerfile gutter, side by side with Docker's own run action.</li>
              <li>Failed engine commands now show a <b>Show Details</b> action with the full CLI error.</li>
            </ul>
            <h4>0.0.5</h4>
            <ul>
              <li>Create Volume / Create Network dialogs now expose the full CLI option set (labels, driver/plugin options, IPv6 subnet, plugin, host-only).</li>
              <li>Updated the Terminal integration to the current (non-deprecated) platform API.</li>
            </ul>
            <h4>0.0.3</h4>
            <ul>
              <li><b>Volumes</b> and <b>Networks</b>: list, create (full CLI option set) and delete.</li>
              <li><b>Container logs</b> streamed into a console; <b>Open Terminal</b> into containers and machines.</li>
              <li><b>Set Kernel…</b> on the engine — install the recommended kernel or a custom tar/binary.</li>
              <li>Docker-style <b>Create Container</b> form (env, ports, volumes, working dir, user, network, resources, <code>--rm</code>).</li>
              <li>Image-reference autocompletion: local images, curated bases and live Docker Hub search.</li>
              <li>Plugin icon now matches the Apple Container logo.</li>
            </ul>
            <h4>0.0.2</h4>
            <ul>
              <li><b>Machine</b> management: create, start, stop, set as default, delete.</li>
              <li>Background auto-refresh; fixed the initial "loading…" state on IDE start.</li>
            </ul>
            <h4>0.0.1</h4>
            <ul>
              <li>Initial release: add the connection, start/stop the engine, browse containers and images, run a container, copy IP/ID.</li>
            </ul>
        """.trimIndent()
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // Channel is derived from the version suffix, with any trailing build
        // number stripped:
        //   0.0.1        -> "default" (Stable)
        //   0.0.1-beta1  -> "beta"    (users subscribe via the beta repository URL)
        channels = listOf(
            version.toString().substringAfter('-', "").trimEnd { it.isDigit() }.ifEmpty { "default" }
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.test {
    useJUnit()
}
