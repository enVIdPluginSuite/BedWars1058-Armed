import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("jvm") version "2.2.21"
    id("com.gradleup.shadow") version "8.3.8"
}

group = "dev.etran"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    // Spigot API 1.8.8 is the compile baseline: it is the lowest supported version, and everything
    // newer is reached through version-safe code paths (see util/ package).
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
        content { includeGroupByRegex("org\\.spigotmc|net\\.md-5") }
    }
}

dependencies {
    // Transitive dependencies are listed by hand: spigot-api 1.8.8 points at a bungeecord-chat snapshot
    // that no longer exists on the snapshot repositories.
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT") { isTransitive = false }
    compileOnly("net.md-5:bungeecord-chat:1.20-R0.2")
    compileOnly("com.google.guava:guava:21.0")
    compileOnly("com.google.code.gson:gson:2.8.9")
    compileOnly("org.yaml:snakeyaml:1.15")
    compileOnly("commons-lang:commons-lang:2.6")
    compileOnly(files("libs/bedwars-api-24.9.jar"))
}

// Java 8 bytecode so the one jar loads on everything from Java 8 (MC 1.8) to Java 25 (MC 26.x).
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        freeCompilerArgs.addAll("-Xjvm-default=all", "-Xno-param-assertions", "-Xno-call-assertions", "-Xno-receiver-assertions")
    }
}

tasks {
    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }

    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("${project.name}-${project.version}.jar")
        // Servers older than 1.16.5 cannot download libraries, so the Kotlin runtime is bundled
        // and relocated to avoid clashing with other Kotlin plugins.
        relocate("kotlin", "dev.etran.armed.libs.kotlin")
        relocate("org.jetbrains", "dev.etran.armed.libs.jetbrains")
        relocate("org.intellij", "dev.etran.armed.libs.intellij")
        minimize()
    }

    build { dependsOn(shadowJar) }
    jar { enabled = false }
}
