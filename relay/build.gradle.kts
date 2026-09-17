plugins {
    kotlin("jvm") version "2.1.10"
    alias(libs.plugins.lombok)
}

group = "com.gato.relay"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.opencollab.dev/maven-releases")
    maven("https://repo.opencollab.dev/maven-snapshots")
}

dependencies {
    implementation(platform(libs.log4j.bom))
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)
    implementation(libs.minecraft.auth)
    implementation(libs.jose4j)
    implementation(libs.jackson.databind)
    implementation(project(":Network:transport-raknet"))
    implementation(project(":Protocol:bedrock-codec"))
    implementation(project(":Protocol:bedrock-connection"))
    implementation(project(":Protocol:common"))
    implementation(libs.bundles.netty)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

tasks.register<Jar>("uberJar") {
    archiveFileName.set("GatoRelay.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .filter { !it.name.startsWith("annotations-") }
            .filter { !it.name.startsWith("kotlin-") }
            .map { zipTree(it) }
    })

    manifest {
        attributes(mapOf(
            "Main-Class" to "com.gato.relay.GatoRelayKt"
        ))
    }

    exclude(
        "META-INF/*.RSA",
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "org/intellij/lang/annotations/**",
        "org/jetbrains/annotations/**",
        "kotlin/**"
    )
}

tasks.jar {
    dependsOn("uberJar")
}