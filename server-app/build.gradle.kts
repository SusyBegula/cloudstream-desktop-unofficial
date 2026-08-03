plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(project(":api-models"))
    // CloudStream Library (KMP, JVM target): MainAPI, extractors, metaproviders, WebViewResolver (JVM actual), etc.
    implementation(project(":library"))
    implementation(project(":android-stubs"))
    implementation(project(":plugin-runtime"))
    implementation(project(":player-abstraction"))
    implementation(project(":common"))

    // ASM Bytecode Scanner (transitively needed by ExtensionLoader's security verifier at runtime)
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-tree:9.6")

    // HTTP
    implementation(libs.nicehttp)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")

    // JSON (Jackson is required by :library/plugin-runtime; kotlinx.serialization is used for the HTTP API)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    implementation(kotlin("reflect"))
    implementation("org.json:json:20240303")
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    val coroutinesVersion = "1.10.2"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    // Playwright (headless Chromium) for Cloudflare bypass
    implementation("com.microsoft.playwright:playwright:1.60.0")

    // BouncyCastle & Conscrypt (Android's built-in AES-GCM crypto isn't available on desktop JVM)
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")
    implementation("org.conscrypt:conscrypt-openjdk-uber:2.5.2")

    // Ktor server: HTTP API + WebSocket + the stream proxy (merged into one engine)
    val ktorVersion = "3.0.3"
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.lagradost.webclient.server.MainKt")
    val javaMajor = JavaVersion.current().majorVersion.toIntOrNull() ?: 21
    if (javaMajor < 24) {
        applicationDefaultJvmArgs = listOf("-Djava.security.manager=allow")
    }
}
