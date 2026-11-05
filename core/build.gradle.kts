import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/**
 * Intentional's platform-free logic: groups and their budget rules, the
 * friction ladder, wind-down, and sync between devices (sync/). The JVM target is what the Android app uses;
 * linuxX64 is for the desktop daemon.
 */
kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
            // Sync: Firebase over REST, encrypted on the device.
            api("io.ktor:ktor-client-core:3.6.1")
            implementation("dev.whyoleg.cryptography:cryptography-core:0.6.0")
        }
        jvmMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.6.1")
            implementation("dev.whyoleg.cryptography:cryptography-provider-jdk:0.6.0")
        }
        linuxX64Main.dependencies {
            implementation("io.ktor:ktor-client-curl:3.6.1")
            // OpenSSL linked in: no libssl version to match on the machine.
            implementation("dev.whyoleg.cryptography:cryptography-provider-openssl3-prebuilt:0.6.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            implementation("io.ktor:ktor-client-mock:3.6.1")
        }
    }
}
