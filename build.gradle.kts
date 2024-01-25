plugins {
    kotlin("jvm") version "1.9.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.21"
}

group = "tech.lq0.modSyncNext"
version = "0.2.2"

tasks.register("generateVersion") {
    doLast {
        project.file("src/main/resources/version").writeText(version.toString())
    }
}

tasks.jar {
    dependsOn("generateVersion")
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0-RC2")
    implementation("net.peanuuutz.tomlkt:tomlkt:0.3.7")
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-coroutines:2.3.1")
    implementation("org.fusesource.jansi:jansi:2.4.1")
}

kotlin {
    jvmToolchain(8)
}