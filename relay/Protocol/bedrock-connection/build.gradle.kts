@file:Suppress("VulnerableLibrariesLocal")

plugins {
    id("java-library")
    alias(libs.plugins.lombok)
    alias(libs.plugins.checkerframework)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.opencollab.dev/maven-releases")
    maven("https://repo.opencollab.dev/maven-snapshots")
}

dependencies {
    // compileOnly(libs.netty.transport.raknet)
    api(project(":Network:transport-raknet"))
    api(project(":Protocol:bedrock-codec"))
    api(libs.snappy)
}