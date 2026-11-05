# Release shrinking (R8). The libraries ship their own rules where they need
# them; what's here covers the gaps.

# Ktor on the JVM references optional classes it never loads on Android.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn io.ktor.utils.io.jvm.javaio.**

# cryptography-kotlin finds its JDK provider through ServiceLoader.
-keep class dev.whyoleg.cryptography.providers.jdk.** { *; }
-keepnames class dev.whyoleg.cryptography.** { *; }

# The guard logs which OEM policy it picked by class name.
-keepnames class dev.josemiguel.launcher.guard.** { *; }

# The guard's accessibility service, receivers and services are kept by the
# manifest; Compose and kotlinx.serialization ship consumer rules.
