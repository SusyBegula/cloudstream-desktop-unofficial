import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

sourceSets {
    main {
        java.srcDirs("src/main/java")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    // CloudStream Library (KMP, JVM target)
    // Contains: MainAPI, extractors, metaproviders, WebViewResolver (JVM actual), etc.
    implementation(project(":library"))

    // ASM Bytecode Scanner
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-tree:9.6")

    // Android Stubs
    implementation(project(":android-stubs"))

    implementation(project(":plugin-runtime"))
    implementation(project(":player-abstraction"))
    implementation(project(":common"))

    // HTTP
    implementation(libs.nicehttp)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")

    // JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    implementation(kotlin("reflect")) // Required for Jackson to deserialize plugin Kotlin data classes
    implementation("org.json:json:20240303") // Required for plugins using org.json (natively included on Android)

    // Coroutines (swing provides Dispatchers.Main on desktop JVM)
    val coroutinesVersion = "1.10.2"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:$coroutinesVersion")

    // Playwright (headless Chromium)
    // Desktop counterpart of Android's WebView system.
    implementation("com.microsoft.playwright:playwright:1.60.0")

    // BouncyCastle & Conscrypt
    // Android's built-in AES-GCM crypto is not available on desktop JVM.
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")
    implementation("org.conscrypt:conscrypt-openjdk-uber:2.5.2")

    // JNA for MPV
    implementation("net.java.dev.jna:jna:5.14.0")

    // Gamepad Controller (Jamepad / SDL2 GameController)
    implementation("com.badlogicgames.gdx:gdx-jnigen-loader:2.5.1")
    implementation("com.badlogicgames.jamepad:jamepad:2.0.14.1") {
        exclude(group = "com.badlogicgames.gdx", module = "gdx-jnigen-loader")
    }

    // Compose Desktop UI
    implementation(compose.desktop.currentOs)
    implementation(compose.material3) // material3 already includes core icons
    implementation(compose.materialIconsExtended)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation("dev.chrisbanes.haze:haze:0.7.3")

    // Image loading
    implementation("io.coil-kt.coil3:coil-compose:3.0.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.0")

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)

    testImplementation(kotlin("test"))
}

// Strip Playwright driver-bundle for current host platform before packaging
// The driver-bundle JAR ships Node.js for ALL platforms (Win/Mac/Linux ~206MB).
// We strip non-target platform binaries to keep installer size small (~50MB).
val stripPlaywrightDriver by tasks.registering {
    description = "Strips unused platform binaries from the Playwright driver-bundle JAR."
    group = "build"

    doLast {
        val driverJar =
            configurations.runtimeClasspath.get()
                .resolvedConfiguration.resolvedArtifacts
                .find { it.name == "driver-bundle" }?.file ?: return@doLast

        val strippedJar = layout.buildDirectory.get().asFile.resolve("playwright-driver-stripped.jar")
        if (strippedJar.exists() && strippedJar.lastModified() > driverJar.lastModified()) {
            println("Playwright driver already stripped, skipping.")
            return@doLast
        }

        val osName = System.getProperty("os.name").lowercase()
        val platformsToStrip =
            when {
                osName.contains("win") -> listOf("driver/mac", "driver/mac-arm64", "driver/linux", "driver/linux-arm64")
                osName.contains("mac") -> listOf("driver/win32-x64", "driver/linux", "driver/linux-arm64")
                else -> listOf("driver/win32-x64", "driver/mac", "driver/mac-arm64") // Retain Linux binaries
            }

        println("Stripping unused platform entries from Playwright driver-bundle (${driverJar.length() / 1024 / 1024}MB)...")

        ZipFile(driverJar).use { input: ZipFile ->
            ZipOutputStream(strippedJar.outputStream().buffered()).use { output: ZipOutputStream ->
                input.entries().asSequence()
                    .filter { entry: ZipEntry ->
                        platformsToStrip.none { entry.name.startsWith(it) }
                    }
                    .forEach { entry: ZipEntry ->
                        output.putNextEntry(ZipEntry(entry.name))
                        if (!entry.isDirectory) {
                            input.getInputStream(entry).copyTo(output)
                        }
                        output.closeEntry()
                    }
            }
        }

        println("Stripped driver size: ${strippedJar.length() / 1024 / 1024}MB (was ${driverJar.length() / 1024 / 1024}MB)")

        // Replace the original JAR in the Gradle cache with the stripped version
        driverJar.delete()
        strippedJar.copyTo(driverJar, overwrite = true)
    }
}

// Compose Desktop application configuration
compose.desktop {
    application {
        mainClass = "com.lagradost.cloudstream3.desktop.MainKt"
        jvmArgs += listOf(
            "-D_JAVA_AWT_WM_NONREPARENTING=1",
            "-Dawt.useSystemAAFontSettings=on",
            "-Dswing.aatext=true",
        )
        val javaMajor = JavaVersion.current().majorVersion.toIntOrNull() ?: 21
        if (javaMajor < 24) {
            jvmArgs += listOf("-Djava.security.manager=allow")
        }

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            val osName = System.getProperty("os.name").lowercase()
            if (osName.contains("win")) {
                targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi)
            } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
                targetFormats(
                    org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                    org.jetbrains.compose.desktop.application.dsl.TargetFormat.AppImage,
                )
            } else {
                targetFormats(
                    org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                    org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                    org.jetbrains.compose.desktop.application.dsl.TargetFormat.AppImage,
                )
            }

            packageName = "CloudStream-Desktop"
            packageVersion = "0.1.0"
            description = "CloudStream Desktop Client"
            vendor = "CloudStream"
            includeAllModules = true // Required — jlink cannot detect dynamically-loaded modules (JNA, Playwright, Conscrypt)
            appResourcesRootDir.set(project.layout.projectDirectory.dir("appResources"))

            windows {
                iconFile.set(project.file("src/main/resources/logo_installer.ico"))
                menuGroup = "CloudStream Desktop"
                upgradeUuid = "d7e9b04f-723a-4467-84df-fcf470c1ae02"
                shortcut = true // Creates a Desktop shortcut during install
                perUserInstall = true // Installs per-user, avoids needing admin rights
            }

            linux {
                iconFile.set(project.file("src/main/resources/logo_ui.png"))
                shortcut = true
                packageName = "cloudstream-desktop"
                appCategory = "Video"
            }
        }
    }

    // Hook the strip task to run before any packaging or distribution task
    afterEvaluate {
        tasks.matching { task ->
            task.name.startsWith("package") || task.name.startsWith("create")
        }.forEach { it.dependsOn(stripPlaywrightDriver) }
    }
}
