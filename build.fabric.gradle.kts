plugins {
    id("dev.kikugie.loom-back-compat")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "sv-voice-changer-fabric"

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

    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    implementation("de.maxhenkel.voicechat:voicechat-api:${property("deps.voicechat_api")}")
    modImplementation("maven.modrinth:$voicechatCoordinate")

    (findProperty("deps.voicechat_stub") as String?)?.let { stubVersion ->
        modRuntimeOnly("de.maxhenkel.voicechat:voicechat-api:$stubVersion:fabric-stub")
    }
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")

    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run/${sc.current.project}")
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
            "id" to project.property("mod.id").toString(),
            "name" to project.property("mod.name").toString(),
            "version" to project.property("mod.version").toString(),
            "description" to project.property("mod.description").toString(),
            "minecraft" to project.property("mod.mc_compat").toString(),
            "voicechat" to project.property("deps.voicechat_compat").toString(),
            "fabric_loader" to project.property("deps.fabric_loader").toString()
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
        filesMatching("*.mixins.json") { expand("java" to "JAVA_${requiredJava.majorVersion}") }
        exclude("META-INF/neoforge.mods.toml")
    }

    jar {
        from(rootProject.file("LICENSE.txt")) {
            rename { "${it}_sv-voice-changer" }
        }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        dependsOn("build")
        from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
        into(rootProject.layout.projectDirectory.dir("dist"))
    }
}
