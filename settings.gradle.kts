pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven("https://jitpack.io")

        // Kotlin/Wasm's toolchain resolves Node.js/binaryen binaries from these ivy repos.
        ivy("https://nodejs.org/dist") {
            name = "Node Distributions at https://nodejs.org/dist"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn Distributions at https://github.com/yarnpkg/yarn/releases/download"
            patternLayout { artifact("v[revision]/yarn-v[revision].tar.gz") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
        ivy("https://github.com/WebAssembly/binaryen/releases/download") {
            name = "Binaryen Distributions at https://github.com/WebAssembly/binaryen/releases/download"
            patternLayout { artifact("version_[revision]/binaryen-version_[revision]-[classifier].[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.github.webassembly", "binaryen") }
        }
    }
}

rootProject.name = "cloudstream-windows"

include(":library")
project(":library").projectDir = file("android-reference/library")

include(":common")
project(":common").projectDir = file("common")

include(":android-stubs")
project(":android-stubs").projectDir = file("android-stubs")

include(":plugin-runtime")
project(":plugin-runtime").projectDir = file("plugin-runtime")

include(":player-abstraction")
project(":player-abstraction").projectDir = file("player-abstraction")

include(":desktop-app")
project(":desktop-app").projectDir = file("desktop-app")

include(":sandbox")
project(":sandbox").projectDir = file("plugin-sandbox")

include(":api-models")
project(":api-models").projectDir = file("api-models")

include(":server-app")
project(":server-app").projectDir = file("server-app")

include(":web-client-core")
project(":web-client-core").projectDir = file("web-client-core")

include(":web-app")
project(":web-app").projectDir = file("web-app")
