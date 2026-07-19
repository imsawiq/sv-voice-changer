pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.6"
    id("dev.kikugie.loom-back-compat") version "0.4"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        fun target(project: String, version: String = project) {
            version("$project-fabric", version).buildscript("build.fabric.gradle.kts")
            version("$project-neoforge", version).buildscript("build.neoforge.gradle.kts")
        }

        target("1.21.1")
        target("1.21.2")
        target("1.21.3")
        target("1.21.4")
        target("1.21.5")
        target("1.21.6")
        target("1.21.7")
        target("1.21.8")
        target("1.21.9")
        target("1.21.10")
        target("1.21.11")
        target("26.1.x", "26.1.2")
        target("26.2.x", "26.2")

        vcsVersion = "1.21.8-fabric"
    }
}

rootProject.name = "Simple Voice Voice Changer"
