plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/**
 * The desktop guard (Omarchy / Hyprland): a Kotlin/Native daemon that counts
 * focused time per window class and, through the Brave extension in
 * browser/, per site, and enforces group budgets on both. One binary, two
 * modes: the daemon, and the extension's native-messaging host.
 */
kotlin {
    linuxX64 {
        binaries.executable {
            baseName = "intentional"
            entryPoint = "dev.josemiguel.intentional.desktop.main"
        }
    }

    sourceSets {
        // The whole module is POSIX calls.
        all { languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi") }
        linuxX64Main.dependencies {
            implementation(project(":core"))
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
        }
        linuxX64Test.dependencies {
            implementation(kotlin("test"))
        }
    }
}
