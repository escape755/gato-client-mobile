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
    api(libs.netty.buffer)
    api(platform(libs.fastutil.bom))
    api(libs.fastutil.int.obj.maps)
    api(libs.fastutil.obj.int.maps)
    api(libs.math)
    api(libs.natives)
}