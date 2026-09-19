plugins {
    id("net.neoforged.moddev") version "2.0.147"
    id("neoforge-mutex")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "sv-voice-changer-neoforge"

val requiredJava = if (sc.current.parsed >= "26.1") JavaVersion.VERSION_25 else JavaVersion.VERSION_21

repositories {
    mavenCentral()
    maven("https://maven.maxhenkel.de/repository/public") { name = "MaxHenkel" }
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
}

dependencies {
    val voicechatCoordinate = (findProperty("deps.voicechat_coordinate") as String?)
        ?: "simple-voice-chat:${property("deps.voicechat")}"

    implementation("de.maxhenkel.voicechat:voicechat-api:${property("deps.voicechat_api")}")
    implementation("maven.modrinth:$voicechatCoordinate")
}

neoForge {
    version = property("deps.neo_loader") as String

    mods {
        register("sv_voice_changer") {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        register("client") {
            gameDirectory = rootProject.file("run/${sc.current.project}")
            client()
        }
    }
}

java {
    withSourcesJar()
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
    toolchain.languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = requiredJava.majorVersion.toInt()
    }

    processResources {
        val props: Map<String, String> = mapOf(
            "id" to project.property("mod.neoforge_id").toString(),
            "name" to project.property("mod.name").toString(),
            "version" to project.property("mod.version").toString(),
            "description" to project.property("mod.description").toString(),
            "minecraft" to project.property("mod.mc_compat").toString(),
            "voicechat" to project.property("deps.voicechat_compat").toString()
        )
        inputs.properties(props)
        filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
        filesMatching("*.mixins.json") { expand("java" to "JAVA_${requiredJava.majorVersion}") }
        exclude("fabric.mod.json")
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    jar {
        from(rootProject.file("LICENSE.txt")) {
            rename { "${it}_sv-voice-changer" }
        }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        dependsOn("build")
        from(jar.flatMap { it.archiveFile }, named<Jar>("sourcesJar").flatMap { it.archiveFile })
        into(rootProject.layout.projectDirectory.dir("dist"))
    }
}
