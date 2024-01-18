plugins {
    kotlin("jvm") version "1.9.21"
}

group = "tech.lq0.modSyncNext"
version = "1.0-SNAPSHOT"

tasks.jar {
    manifest {
        attributes["Main-Class"] = "tech.lq0.modSyncNext.MainKt"
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    configurations["compileClasspath"].forEach { file ->
        from(zipTree(file.absoluteFile))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
    implementation("net.peanuuutz.tomlkt:tomlkt:0.3.7")
}

kotlin {
    jvmToolchain(8)
}